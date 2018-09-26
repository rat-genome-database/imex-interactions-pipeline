package edu.mcw.rgd.pipelines.imexinteractions;


import edu.mcw.rgd.datamodel.Interaction;
import edu.mcw.rgd.process.Utils;
import psidev.psi.mi.tab.model.BinaryInteraction;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created by jthota on 3/14/2016.
 */
public class Process {
    private int minFileSize = 27800;
    Dao dao = new Dao();
    Date startDate = new Date();
    private String deleteThreshold;

    public int processFile(String fileName) throws Exception {
        long time0 = System.currentTimeMillis();

        int initialAttrCount = dao.getAttributeCount();
        List<Interaction> piList = loadProteinInteractions(fileName);

        int insertedRecordCount = dao.insertOrUpdate(piList);

        int fileSize = dao.getFileSize(fileName);
        if( fileSize>=getMinFileSize() ){
             int deletedInteractionsCount = dao.deleteUnmodifiedInteractions(startDate);
             System.out.println("Deleted Interaction Records Count: " + deletedInteractionsCount);
        }

        int deletedInteractionAttributesCount = dao.deleteUnmodifiedInteractionAttributes(startDate, getDeleteThreshold(), initialAttrCount);
        System.out.println("Deleted Interaction Attributes Count: " + deletedInteractionAttributesCount);

        System.out.println("===PROCESSING ELAPSED TIME: "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        return insertedRecordCount;
    }

    List<Interaction> loadProteinInteractions(String fileName) throws Exception {

        BufferedInputStream br = new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName)));

        List<Interaction> piList1 = new ArrayList<>();
        Parser parser = new Parser();

        psidev.psi.mi.tab.io.PsimiTabReader reader = new psidev.psi.mi.tab.PsimiTabReader();
        Iterator<BinaryInteraction> i$ = reader.iterate(br);
        int count = 0;

        while(i$.hasNext()){
            count++;
            BinaryInteraction bi= i$.next();
            List<Interaction> piList2= parser.parseInteraction(bi);

            if( piList2.size()>0 ){
                piList1.addAll(piList2);
            }
        }

        br.close();

        System.out.println("Downloaded Record Count: " + count);
        System.out.println("Records with proteins having RGD_IDs:  " + piList1.size());
        System.out.println("Count of incoming unassigned pubmed values (duplicates possible): " + parser.countOfUnassignedPubmedValues);

        return piList1;
    }

    public int getMinFileSize() {
        return minFileSize;
    }

    public void setMinFileSize(int minFileSize) {
        this.minFileSize = minFileSize;
    }

    public void setDeleteThreshold(String deleteThreshold) {
        this.deleteThreshold = deleteThreshold;
    }

    public String getDeleteThreshold() {
        return deleteThreshold;
    }
}
