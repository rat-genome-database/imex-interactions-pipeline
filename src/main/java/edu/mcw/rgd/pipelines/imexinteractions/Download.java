package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by jthota on 2/25/2016.
 */
public class Download {
    private List<String> identifiers;
    private String imexRegistryUrl;
    private int apiRequestsMade = 0;
    private int failedRequests = 0;
    private int maxRetryCount;
    private int retryCount = 0;

    Logger log = Logger.getLogger("main");
    private String agrUrl;

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

           log.info("No. OF IMEX DATABASES URLS: " + getIdentifiers().size());

            // download code with retry:
            //   it takes hours to download all the data
            //   and it happens often that the downloaded data is partial
            // therefore we want to perform a preset number of retries when the download fails
            List<DownloadInfo> downloadList = new ArrayList<>();

            for(String dbIdentifier: getIdentifiers()) {
                for(String species: allSpecies){
                    DownloadInfo di = new DownloadInfo();
                    di.dbUri = dbIdentifier.trim();
                    di.speciesName = species;
                    downloadList.add(di);
                }
            }

            final String outTmpFileName = "data/tmp.gz";
            long totalBytesRead = 0;
            int retryCount = 0;
            while( retryCount<=getMaxRetryCount() && !downloadList.isEmpty() ) {

                Collections.shuffle(downloadList);
                DownloadInfo di = downloadList.remove(0);

                // first download everything to a temporary file
                OutputStream outputStreamTmp = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outTmpFileName)));

                log.info("DOWNLOADING FROM: " + di.dbUri +" for "+di.speciesName);
                PsicquicClient client = new PsicquicClient(di.dbUri);

                apiRequestsMade++;
                long bytesReadForSpecies = 0;
                String taxId = SpeciesType.getTaxonomicId(SpeciesType.parse(di.speciesName))+"";
                try {
                    final InputStream result = client.getByQuery("species:"+taxId);
                    int wasRead;
                    while ((wasRead = result.read(bytes, 0, bytes.length)) >= 0) {
                        outputStreamTmp.write(bytes, 0, wasRead);
                    }
                    result.close();

                    // incoming request file was fully downloaded to tmp file -- copy this tmp file to the main output file
                    outputStreamTmp.close();

                    try( InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(outTmpFileName))) ) {
                        while ((wasRead = in.read(bytes, 0, bytes.length)) >= 0) {
                            bytesReadForSpecies += wasRead;
                            outputStream.write(bytes, 0, wasRead);
                        }
                    }
                }catch(Exception e){
                    log.warn("WARNING! "+di.dbUri + " REQUEST FAILED for "+di.speciesName+": "+e.getMessage());
                    // retry it
                    downloadList.add(di);
                    retryCount++;
                    log.warn("   request will be retried; current download retry count: "+retryCount);
                    outputStreamTmp.close(); // most likely partially downloaded file
                }

                log.info("   bytes read: " + bytesReadForSpecies+"      pending reqs: "+downloadList.size());
                if( bytesReadForSpecies>0 ) {
                    Long totalBytesReadForSpecies = bytesReadForSpeciesMap.get(di.speciesName);
                    if (totalBytesReadForSpecies == null) {
                        totalBytesReadForSpecies = 0l;
                    }
                    bytesReadForSpeciesMap.put(di.speciesName, totalBytesReadForSpecies + bytesReadForSpecies);
                    totalBytesRead += bytesReadForSpecies;
                }
            }
            log.info("TOTAL BYTES READ " + Utils.formatThousands(totalBytesRead));

            setFailedRequests(downloadList.size());
            setRetryCount(retryCount);
        }
        outputStream.close();

        log.info("");
        log.info("BYTES READ FOR SPECIES");
        for( Map.Entry<String, Long> entry: bytesReadForSpeciesMap.entrySet() ) {
            log.info("  "+entry.getKey()+": "+ Utils.formatThousands(entry.getValue()));
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
            log.info("NEW IMEX DB URLS: (unprocessed by the pipeline) !!!");
            for( String newImexDbUrl: newImexDbUrls ) {
                log.info("  " + newImexDbUrl);
            }
            log.info("===");
        }
    }

    public String downloadAgrFile() throws Exception {
        FileDownloader fd = new FileDownloader();
        fd.setExternalFile(getAgrUrl());
        fd.setLocalFile("data/Alliance_interactions.tar.gz");
        fd.setPrependDateStamp(true);
        String localFile = fd.downloadNew();

        // expand tar file
        String tarFileName = "data/Alliance_interactions.tar";
        byte[] buffer = new byte[4096];

        GZIPInputStream gZIPInputStream = new GZIPInputStream(new FileInputStream(localFile));
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tarFileName));

        int bytes_read;
        while ((bytes_read = gZIPInputStream.read(buffer)) > 0) {
            out.write(buffer, 0, bytes_read);
        }

        gZIPInputStream.close();
        out.close();


        System.out.println("Ungzipped to "+tarFileName);

        String outFileName = localFile.replace(".tar.gz", ".mitab.gz");
        out = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(outFileName)));

        try (TarArchiveInputStream fin = new TarArchiveInputStream(new FileInputStream(tarFileName))){
            TarArchiveEntry entry;
            while ((entry = fin.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                while ((bytes_read = fin.read(buffer)) > 0) {
                    out.write(buffer, 0, bytes_read);
                }
                out.close();
                break;
            }
        }

        System.out.println("Untarred to "+outFileName);

        new File(tarFileName).delete();
        System.out.println("Deleted "+tarFileName);


        return outFileName;
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

    public int getApiRequestsMade() {
        return apiRequestsMade;
    }

    public void setApiRequestsMade(int apiRequestsMade) {
        this.apiRequestsMade = apiRequestsMade;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(int failedRequests) {
        this.failedRequests = failedRequests;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setAgrUrl(String agrUrl) {
        this.agrUrl = agrUrl;
    }

    public String getAgrUrl() {
        return agrUrl;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    class DownloadInfo {
        public String dbUri;
        public String speciesName;
    }
}
