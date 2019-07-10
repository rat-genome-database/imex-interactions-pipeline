package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.datamodel.Interaction;
import edu.mcw.rgd.datamodel.InteractionAttribute;

import psidev.psi.mi.tab.model.BinaryInteraction;
import psidev.psi.mi.tab.model.Confidence;
import psidev.psi.mi.tab.model.CrossReference;

import org.apache.log4j.Logger;
import java.util.*;

/**
 * Created by jthota on 3/11/2016.
 */
public class Parser {

    Logger log = Logger.getLogger("main");
    Dao dao = new Dao();
    int countOfUnassignedPubmedValues = 0;

    /**
     * Parses one BinaryInteraction and returns list of interaction records based on returned Protein rgdIds.
     *
     * @param bi BinaryInteraction.
     * @return list of Interaction objects
     * @throws Exception
     */
    public List<Interaction> parseInteraction(BinaryInteraction bi) throws Exception {
        List<Interaction> piList = new ArrayList<>();
        List<Interactor> iList = new ArrayList<>();

        if (bi.getInteractorA() != null && bi.getInteractorB() != null) {

            String p1 = bi.getInteractorA().getIdentifiers().iterator().next().getIdentifier();
            String p2 = bi.getInteractorB().getIdentifiers().iterator().next().getIdentifier();

            if (p1 != null && p2 != null) {
                iList = this.getInteractorRgdIds(p1, p2);
            }

            List<InteractionAttribute> aList = this.parseAttributes(bi);
            String iType = this.parseInteractionType(bi);

            for (Interactor i : iList) {
                Interaction pi = new Interaction();
                pi.setRgdId1(i.getRgdId1());
                pi.setRgdId2(i.getRgdId2());
                pi.setInteractionType(iType);
                pi.setInteractionAttributes(aList);
                piList.add(pi);
            }
        }

        return piList;
    }

    /**
     * Returns the list of interactors
     *
     * @param p1 accession id for protein 1
     * @param p2 accession id for protein 2
     * @return List of Interactor objects
     */
    public List<Interactor> getInteractorRgdIds(String p1, String p2) throws Exception {

        List<Interactor> iList = new ArrayList<>();
        if (p1 == null || p2 == null ) {
            return iList;
        }

        Set<Integer> rgdIds1 = new HashSet<>();
        Set<Integer> rgdIds2 = new HashSet<>();

        int rgdId1 = dao.getProteinRgdid(p1);
        int rgdId2 = dao.getProteinRgdid(p2);

        if( rgdId1!=0 ) {
            rgdIds1.add(rgdId1);
        } else {
            for( Integer secRgdId: dao.getRgdIdsWithSecondaryUniProtIds(p1) ) {
                rgdIds1.add(secRgdId);
            }
        }

        if( rgdId2!=0 ) {
            rgdIds2.add(rgdId2);
        } else {
            for( Integer secRgdId: dao.getRgdIdsWithSecondaryUniProtIds(p2) ) {
                rgdIds2.add(secRgdId);
            }
        }

        for( int rgd1: rgdIds1 ) {
            for( int rgd2: rgdIds2 ) {
                Interactor i = new Interactor();
                i.setRgdId1(rgd1);
                i.setRgdId2(rgd2);
                iList.add(i);
            }
        }

        // dump protein pairs without match in RGD
        if( rgdIds1.isEmpty() || rgdIds2.isEmpty() ) {
            log.debug(p1 + " | " + p2);
        }

        return iList;
    }

    /**
     * Parses one BinaryInteraction and returns List of attributes of that Binary Interaction.
     *
     * @param bi BinaryInteraction
     * @return List of InteractionAttribute objects
     */
    public List<InteractionAttribute> parseAttributes(BinaryInteraction bi) {
        List<InteractionAttribute> aList = new ArrayList<>();
        for(Confidence c: (List<Confidence>) bi.getConfidenceValues()){
            String name=c.getType();
            String value=c.getValue().toString();
            if(value.length()<100) {
                InteractionAttribute a = new InteractionAttribute();
                a.setAttributeName(name);
                a.setAttributeValue(value);
                aList.add(a);

                // mtutaj: this must be commented out, because on PROD it produces so much output that the pipeline summary email cannot be sent
                // System.out.println(c.getText() + "\t" + c.getValue() + "\t" + c.getType());
            }
        }
        for (CrossReference p: (List<CrossReference>)bi.getPublications() ) {
            String name = p.getDatabase();
            String value = p.getIdentifier();
            if( value.startsWith("unassigned") ) {
                countOfUnassignedPubmedValues++;
            } else {
                InteractionAttribute a = new InteractionAttribute();
                a.setAttributeName(name);
                a.setAttributeValue(value);
                aList.add(a);
            }
        }

        for( CrossReference s: (List<CrossReference>) bi.getSourceDatabases() ) {
            String name = "sourcedb";
            String value = s.getText();
            InteractionAttribute a = new InteractionAttribute();
            a.setAttributeName(name);
            a.setAttributeValue(value);
            aList.add(a);
        }

        for( CrossReference intAc: (List<CrossReference>) bi.getInteractionAcs() ){
            InteractionAttribute a= new InteractionAttribute();
            if( intAc.getDatabase().equalsIgnoreCase("imex") ){
                a.setAttributeName("interaction_ac");
                a.setAttributeValue(intAc.getIdentifier());
                aList.add(a);
            }
            else if( intAc.getDatabase().equalsIgnoreCase("biogrid") ){
                a.setAttributeName("interaction_ac");
                a.setAttributeValue("biogrid:"+intAc.getIdentifier());
                aList.add(a);
            }
        }
        return aList;
    }

    /**
     * Parses one BinaryInteraction and returns its interactionType identifier
     *
     * @param bi BinaryInteraction
     * @return identifier for interaction type
     */
    public String parseInteractionType(BinaryInteraction bi) {
        List<CrossReference> interactionTypes = bi.getInteractionTypes();
        for( CrossReference interactionType: interactionTypes ) {
            return interactionType.getIdentifier();
        }
        return "";
    }

}
