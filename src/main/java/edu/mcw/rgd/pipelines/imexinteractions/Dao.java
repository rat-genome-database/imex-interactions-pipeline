package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.object.BatchSqlUpdate;

import java.sql.Types;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jthota
 * @since 3/11/2016
 *  A wrapper class to all the DAOs for database operations.
 */
public class Dao extends AbstractDAO{

    Logger log = LogManager.getLogger("status");
    Logger logDeleted = LogManager.getLogger("deleted");
    Logger log_inserted =LogManager.getLogger("inserted");
    Logger log_modified = LogManager.getLogger("modified");
    Logger logDeletedAttrs = LogManager.getLogger("deletedAttrs");

    private XdbIdDAO xdbDao= new XdbIdDAO();
    private ProteinDAO proteinDAO = new ProteinDAO();
    private InteractionsDAO idao= new InteractionsDAO();
    private InteractionAttributesDAO adao= new InteractionAttributesDAO();

    /**
     * Returns list RGD_IDs of secondary uniprot id
     * @param accId uniprot secondary Accession Id
     * @return
     * @throws Exception
     */

    public List<Integer> getRgdIdsWithSecondaryUniProtIds(String accId) throws Exception {

        List<Integer> rgdIds = new ArrayList<>();
        for(RgdId id: xdbDao.getRGDIdsByXdbId(XdbId.XDB_KEY_UNIPROT_SECONDARY, accId) ) {
            if( id.getObjectStatus().equals("ACTIVE") && id.getObjectKey()==RgdId.OBJECT_KEY_PROTEINS ) {
                rgdIds.add(id.getRgdId());
            }
        }
        return rgdIds;
    }

    /**
     * Returns RGD_ID of uniport protein Id
     * @param uniprotId uniprot protein accession Id
     * @return
     * @throws Exception
     */
    public int getProteinRgdid(String uniprotId) throws Exception {

        Integer rgdId = mapUniprotId2ProteinRgdId.get(uniprotId);
        if( rgdId!=null ) {
            return rgdId;
        }

        // uniprot accessions always start with a letter
        char c = uniprotId.charAt(0);
        if( Character.isLetter(c) ) {
            Protein protein = proteinDAO.getProteinByUniProtId(uniprotId);
            rgdId = protein == null ? 0 : protein.getRgdId();
            mapUniprotId2ProteinRgdId.put(uniprotId, rgdId);
            return rgdId;
        }

        // NCBI gene accessions from biogrid start with a number
        List<Gene> genes = getActiveGenesByNcbiGeneId(uniprotId);
        if( genes.isEmpty() ) {
            rgdId = 0;
        }
        else if( genes.size()>1 ) {
            log.warn("multiple genes matching NCBI gene id "+uniprotId);
            rgdId = 0;
        } else {
            rgdId = genes.get(0).getRgdId();
        }
        mapUniprotId2ProteinRgdId.put(uniprotId, rgdId);
        return rgdId;
    }

    Map<String,Integer> mapUniprotId2ProteinRgdId = new HashMap<>();

    List<Gene> getActiveGenesByNcbiGeneId(String accId) throws Exception {
        // NCBI gene accessions from biogrid start with a number
        List<Gene> genes = xdbDao.getActiveGenesByXdbId(XdbId.XDB_KEY_NCBI_GENE, accId);

        // exclude splices and alleles
        Iterator<Gene> it = genes.iterator();
        while( it.hasNext() ) {
            Gene g = it.next();
            if( g.isVariant() ) {
                it.remove();
            }
        }
        return genes;
    }

    /**
     * Insert or Update the List of Protein Interactions
     * @param piList ProteinInteractionList
     * @return count of interactions inserted into db
     * @throws Exception
     */
    public int insertOrUpdate(Collection<Interaction> piList) throws Exception{

        AtomicInteger count = new AtomicInteger(0);

        // existing interactions only need their last_modified_date refreshed; collect their keys
        // (deduplicated) and refresh them in one batch instead of one update per interaction --
        // this per-row update used to be a major hotspot (~40% of runtime)
        Set<Integer> existingInteractionKeys = ConcurrentHashMap.newKeySet();

        piList.parallelStream().forEach( pi -> {
            try {
                count.addAndGet(insertOrUpdate(pi, existingInteractionKeys));
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        int refreshed = batchRefreshInteractionLastModifiedDate(existingInteractionKeys);
        log_modified.debug("Refreshed last_modified_date for existing interactions: " + refreshed);

        return count.intValue();
    }

    /**
     * Refresh last_modified_date for the given interaction keys in a single JDBC batch,
     * replacing a per-interaction update that was a major hotspot.
     * @return count of rows updated
     */
    int batchRefreshInteractionLastModifiedDate(Set<Integer> interactionKeys) throws Exception {
        if( interactionKeys.isEmpty() ) {
            return 0;
        }
        String sql = "UPDATE interactions SET last_modified_date=SYSDATE WHERE interaction_key=?";
        BatchSqlUpdate su = new BatchSqlUpdate(this.getDataSource(), sql, new int[]{ Types.INTEGER });
        su.compile();
        for( int key: interactionKeys ) {
            su.update(key);
        }
        return executeBatch(su);
    }

    /**
     * Insert a new interaction record or Update last modified date of existing record
     * @param pi
     * @return
     * @throws Exception
     */
    public int insertOrUpdate(Interaction pi, Set<Integer> existingInteractionKeys) throws  Exception{
        int newRecCount=0;
        int key = idao.getInteractionKey(pi);
        if(key!=0){
            pi.setInteractionKey(key);
            // existing interaction: defer the last_modified_date bump to a single batch (see caller)
            existingInteractionKeys.add(key);
            int attCount = adao.updateAttributes(pi);
            if(attCount>0){
                log_inserted.debug("New Attribute Records to Existing Interaction: " + key + " - " +attCount);
            }
            log_modified.debug("Updated: " + pi.getInteractionKey() + "|" + pi.getRgdId1() + "|" + pi.getRgdId2() + "|" + pi.getInteractionType());
            return 0;
        }

        key= this.getNextKey("interactions_seq");
        pi.setInteractionKey(key);
        int intCount = idao.insert(pi);
        if(intCount>0){
            log_inserted.debug("NEW INTERACTION: " + pi.getInteractionKey() +"|"+pi.getRgdId1()+"|"+pi.getRgdId2() +"|"+ pi.getInteractionType());
        }
        for( InteractionAttribute a: pi.getInteractionAttributes() ){
            int aKey= adao.getNextKey("interactionAttributes_seq");
            a.setAttributeKey(aKey);
            a.setInteractionKey(key);
            newRecCount += adao.insert(a);
        }
        if(newRecCount>0) {
           log_inserted.debug("New Attribute Record for New INTERACTION:  "+ key  + " - " + newRecCount);
        }
        return intCount;
    }

    /**
     * Delete Interactions modified before the given Date.
     * @param date cut-off date
     * @return count of stale interactions, i.e. interactions to be deleted
     * @throws Exception
     */
    public int deleteUnmodifiedInteractions(Date date) throws Exception {

        int deleted =0;
        int skipped = 0;

        List<Interaction> piList = idao.getInteractionsModifiedBeforeTimeStamp(date);

        log.info("STALE INTERACTIONS COUNT: " + piList.size());
        logDeleted.info("STALE INTERACTIONS COUNT: " + piList.size());

        if (piList.size() != 0) {
            logDeleted.info("DELETING UNMODIFIED INTERACTIONS ...");

            for (Interaction pi : piList) {

                int key = pi.getInteractionKey();
                try {
                    deleted += idao.deleteUnmodifiedInteractions(key);

                    logDeleted.debug(pi.getInteractionKey() + "|" + pi.getRgdId1() + "|" + pi.getRgdId2() + "|" + pi.getInteractionType() + "|" + pi.getCreatedDate() + "|" + pi.getLastModifiedDate());

                } catch( java.sql.SQLIntegrityConstraintViolationException e ) {
                    skipped++;
                    logDeleted.debug("ERROR: cannot delete: nested exception is java.sql.SQLIntegrityConstraintViolationException: ORA-02292: integrity constraint (CURPROD.INTERACTION_ATTRIBUTES_FK) violated - child record found");
                    logDeleted.debug("ERROR: "+pi.getInteractionKey() + "|" + pi.getRgdId1() + "|" + pi.getRgdId2() + "|" + pi.getInteractionType() + "|" + pi.getCreatedDate() + "|" + pi.getLastModifiedDate());
                }
            }
            logDeleted.info("DELETION COMPLETE");
        }

        if( skipped>0 ) {
            log.info("WARNING: some interactions could not be deleted (db integrity constraints violated): " + skipped);
        }

        return deleted;
    }

    public int deleteUnmodifiedInteractionAttributes(Date cutoffDate, String deleteThresholdStr, int initialAttrCount) throws Exception {

        String msg = "INITIAL INTERACTION ATTRIBUTES COUNT: " + Utils.formatThousands(initialAttrCount);
        log.info(msg);

        // convert delete-threshold-string to integer
        // f.e. '5%' ==> 5
        int deleteThresholdInPercent = Integer.parseInt(deleteThresholdStr.substring(0, deleteThresholdStr.indexOf('%')));

        // final attribute count after pipeline has finished running
        int finalAttrCount = adao.getAttributeCount();
        msg = "FINAL INTERACTION ATTRIBUTES COUNT: " + Utils.formatThousands(finalAttrCount);
        log.info(msg);

        List<InteractionAttribute> staleAttributes = adao.getUnmodifiedAttributes(cutoffDate);
        int staleAttrCount = staleAttributes.size();
        msg = "STALE INTERACTION ATTRIBUTES COUNT: " + Utils.formatThousands(staleAttrCount);
        log.info(msg);

        // do not delete more than 5% of attributes
        int deleteThreshold = (deleteThresholdInPercent * finalAttrCount) / 100;
        msg = "  STALE INTERACTION ATTRIBUTES DELETE THRESHOLD ("+deleteThresholdInPercent+"%):  " + Utils.formatThousands(deleteThreshold);
        log.info(msg);

        if( staleAttrCount>deleteThreshold ) {
            msg = "  *** MORE STALE INTERACTION ATTRIBUTES THAN DELETE THRESHOLD!";
            log.info(msg);
            return 0;
        }

        for( InteractionAttribute attr: staleAttributes ) {
            String info = "";
            info += "AKEY="+attr.getAttributeKey();
            info += "|IKEY="+attr.getInteractionKey();
            info += "|NAME="+attr.getAttributeName();
            info += "|VALUE="+attr.getAttributeValue();
            info += "|CREATED="+attr.getCreatedDate();
            info += "|LASTMOD="+attr.getLastModifiedDate();

            logDeletedAttrs.debug(info);
        }

        int deletedAttrCount = adao.deleteUnmodifiedAttributes(cutoffDate);
        msg = "  STALE INTERACTION ATTRIBUTES DELETED: " + Utils.formatThousands(deletedAttrCount);
        log.info(msg);
        return deletedAttrCount;
    }

    public int getInteractionCountForSpecies(int speciesTypeKey) throws Exception {
        return idao.getInteractionCountForSpecies(speciesTypeKey);
    }

    public int getAttributeCount() throws Exception {
        return adao.getAttributeCount();
    }
}
