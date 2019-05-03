package edu.mcw.rgd.pipelines.imexinteractions;


import edu.mcw.rgd.datamodel.Interaction;
import edu.mcw.rgd.datamodel.InteractionAttribute;
import edu.mcw.rgd.process.Utils;
import psidev.psi.mi.tab.model.BinaryInteraction;

import java.io.BufferedReader;
import java.util.*;

/**
 * Created by jthota on 3/14/2016.
 */
public class Process {
    Dao dao = new Dao();
    private String deleteThreshold;

    public int processFiles(List<String> fileNames, boolean processStaleData) throws Exception {
        long time0 = System.currentTimeMillis();

        int initialAttrCount = dao.getAttributeCount();

        List<Interaction> piList = new ArrayList<>();
        for( String fileName: fileNames ) {
            piList.addAll( loadProteinInteractions(fileName) );
        }

        Collection<Interaction> piUnique = mergeDuplicateInteractions(piList);

        long time1 = System.currentTimeMillis();
        int insertedRecordCount = dao.insertOrUpdate(piUnique);
        System.out.println(" --- insertOrUpdate: elapsed "+Utils.formatElapsedTime(time1, System.currentTimeMillis()));

        if( processStaleData ) {
            // set the start date to yesterday: pipeline server and db server could be slightly out-of-sync,
            // and we don't want to delete the just added interactions and attributes
            Date startDate = Utils.addDaysToDate(new Date(), -1);

            int deletedInteractionsCount = dao.deleteUnmodifiedInteractions(startDate);
            System.out.println("Deleted Interaction Records Count: " + deletedInteractionsCount);

            int deletedInteractionAttributesCount = dao.deleteUnmodifiedInteractionAttributes(startDate, getDeleteThreshold(), initialAttrCount);
            System.out.println("Deleted Interaction Attributes Count: " + deletedInteractionAttributesCount);
        }

        System.out.println("===PROCESSING ELAPSED TIME: "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        return insertedRecordCount;
    }

    List<Interaction> loadProteinInteractions(String fileName) throws Exception {

        System.out.println("Loading "+fileName + " ...");

        BufferedReader br = Utils.openReader(fileName);

        List<Interaction> piList1 = new ArrayList<>();
        Parser parser = new Parser();

        psidev.psi.mi.tab.io.PsimiTabReader reader = new psidev.psi.mi.tab.PsimiTabReader();
        int count = 0;

        for( BinaryInteraction bi: reader.read(br) ){
            count++;
            List<Interaction> piList2= parser.parseInteraction(bi);

            if( piList2.size()>0 ){
                piList1.addAll(piList2);
            }
        }

        br.close();

        System.out.println("Downloaded Record Count: " + count);
        System.out.println("Records with proteins having RGD_IDs:  " + piList1.size());
        System.out.println("Count of incoming unassigned pubmed values (duplicates possible): " + parser.countOfUnassignedPubmedValues);
        System.out.println();

        return piList1;
    }

    Collection<Interaction> mergeDuplicateInteractions( List<Interaction> interactions ) {
        // unique interaction in rgd is identified by compound key: {rgdid1, rgdid2, interactiontype}
        Map<String, Interaction> map = new HashMap<>(interactions.size());

        for( Interaction i: interactions ) {
            String compoundKey = i.getRgdId1()+"-"+i.getRgdId2()+"-"+i.getInteractionType();
            Interaction uniqueI = map.get(compoundKey);
            if( uniqueI==null ) {
                map.put(compoundKey, i);
            } else {
                // only merge interaction attributes
                uniqueI.getInteractionAttributes().addAll(i.getInteractionAttributes());
            }
        }
        System.out.println("   ORIGINAL INCOMING INTERACTION COUNT: "+interactions.size());
        System.out.println("   MERGED   INCOMING INTERACTION COUNT: "+map.size());

        // merge attributes
        int origAttrCount = 0;
        int newAttrCount = 0;
        for( Interaction i: map.values() ) {
            origAttrCount += i.getInteractionAttributes().size();
            Set<InteractionAttribute> attrs = new HashSet<>(i.getInteractionAttributes());
            newAttrCount += attrs.size();
            i.setInteractionAttributes(new ArrayList<>(attrs));
        }

        System.out.println("   ORIGINAL INCOMING ATTR COUNT: "+origAttrCount);
        System.out.println("   MERGED   INCOMING ATTR COUNT: "+newAttrCount);
        return map.values();
    }

    public void setDeleteThreshold(String deleteThreshold) {
        this.deleteThreshold = deleteThreshold;
    }

    public String getDeleteThreshold() {
        return deleteThreshold;
    }
}
