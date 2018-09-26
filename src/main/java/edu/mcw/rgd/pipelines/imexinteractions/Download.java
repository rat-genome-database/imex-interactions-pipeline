package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.zip.GZIPOutputStream;

/**
 * Created by jthota on 2/25/2016.
 */
public class Download {
    private List<String> identifiers;
    private String imexRegistryUrl;

    Logger log = Logger.getLogger("Failure");

    /**
     * DOWNLOADS INTERACTIONS OF ALL SPECIES TO A LOCAL FILE AND RETURNS FILE NAME.
     * @param allSpecies
     * @return
     * @throws Exception
     */
    public String download2File(List<String> allSpecies, String fileName) throws Exception {

        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd");
        String outfilename="data/Interactions_"+fileName + "_" + date.format(new Date()) + ".gz" ;

        OutputStream outputStream = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outfilename)));
        byte[] bytes = new byte[4096];

        // these identifiers are only for us to see if there are any new IMEX databases available
        detectNewImexDbUrls();

        Map<String,Long> bytesReadForSpeciesMap = new HashMap<>();

        if( !getIdentifiers().isEmpty() ) {
            System.out.println("No. OF IMEX DATABASES URLS: " + getIdentifiers().size());
            for(String dbIdentifier: getIdentifiers()) {
                System.out.println("DOWNLOADING FROM: " + dbIdentifier);
                PsicquicClient client = new PsicquicClient(dbIdentifier.trim());
                long totalBytesRead = 0;
                for(String species: allSpecies){
                    long bytesReadForSpecies = 0;
                    String taxId = SpeciesType.getTaxonomicId(SpeciesType.parse(species))+"";
                    try {
                        final InputStream result = client.getByQuery("species:"+taxId);
                        int wasRead;
                        while ((wasRead = result.read(bytes, 0, bytes.length)) >= 0) {
                            bytesReadForSpecies += wasRead;
                            outputStream.write(bytes, 0, wasRead);
                        }
                        result.close();
                    }catch(Exception e){
                        log.info(dbIdentifier + "STATUS NOT AVAILABLE");
                    }

                    Long totalBytesReadForSpecies = bytesReadForSpeciesMap.get(species);
                    if( totalBytesReadForSpecies==null ) {
                        totalBytesReadForSpecies = 0l;
                    }
                    bytesReadForSpeciesMap.put(species, totalBytesReadForSpecies+bytesReadForSpecies);
                    totalBytesRead += bytesReadForSpecies;
                }
                System.out.println("  bytes read " + totalBytesRead);
            }
        }
        outputStream.close();

        System.out.println("BYTES READ FOR SPECIES");
        for( Map.Entry<String, Long> entry: bytesReadForSpeciesMap.entrySet() ) {
            System.out.println("  "+entry.getKey()+": "+ Utils.formatThousands(entry.getValue()));
        }

        return outfilename;
    }

   /**
    * Returns list of web Service URLs of IMEX Curated Databases
    */
    public List<String> getImexDbUrls() throws Exception {

        List<String> imexURLs = new ArrayList<>();

        URL registryURL = new URL(getImexRegistryUrl());
        HttpURLConnection registryConnection = (HttpURLConnection) registryURL.openConnection();
        registryConnection.setRequestMethod("GET");
        int responseCode = registryConnection.getResponseCode();

        if(responseCode==200){

            log.info("IMEX REGISTRY (LIVE):");
            BufferedReader br = new BufferedReader(new InputStreamReader(registryConnection.getInputStream()));
            String line;
            while((line= br.readLine())!=null) {
                if(line.contains("REST:")){
                    String[] strings =line.split("REST:");
                    if(strings[1].endsWith("<br/>")) {
                        String url = strings[1].substring(0, strings[1].indexOf("<")).trim();
                        log.info("  "+url);
                        imexURLs.add(url);
                    }
                }
            }
            br.close();
            registryConnection.disconnect();
            log.info("====");
        }

        return imexURLs;
    }

    void detectNewImexDbUrls() throws Exception {

        List<String> imedDbUrls = this.getImexDbUrls();

        List<String> newImexDbUrls = new ArrayList<>();
        for( String imexDbUrl: imedDbUrls ) {
            // check if imex db url as read from imex registry is a new unknown IMEX db
            boolean isNew = true;
            for( String knownImexDb: getIdentifiers() ) {
                if( knownImexDb.equals(imexDbUrl)) {
                    isNew = false;
                    break;
                }
            }
            if( isNew ) {
                newImexDbUrls.add(imexDbUrl);
            }
        }

        if( !newImexDbUrls.isEmpty() ) {
            System.out.println("NEW IMEX DB URLS: (unprocessed by the pipeline) !!!");
            for( String newImexDbUrl: newImexDbUrls ) {
                System.out.println("  " + newImexDbUrl);
            }
            System.out.println("===");
        }
    }

    public List<String> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<String> identifiers) {
        this.identifiers = identifiers;
    }

    public void setImexRegistryUrl(String imexRegistryUrl) {
        this.imexRegistryUrl = imexRegistryUrl;
    }

    public String getImexRegistryUrl() {
        return imexRegistryUrl;
    }
}
