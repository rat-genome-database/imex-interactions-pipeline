package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.Utils;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

/**
 * Created by jthota on 2/25/2016.
 */
public class Manager {
    private String version;
    private List<String> filenames = new ArrayList<>();
    private List<String> supportedSpecies;

    Process process;

    /**
     * PARSE THE ARGUMENTS AND MAKE METHOD CALLS TO DOWNLOAD AND PROCESS
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));
        System.out.println(manager.getVersion());

        if (args.length ==0 || args.length<2) {
            manager.printUsageAndExit();
        }

        Download download = (Download) (bf.getBean("download"));
        manager.process = (Process) (bf.getBean("process"));

        manager.run(args, download);
    }

    void run(String[] args, Download download) throws Exception {

        long time0 = System.currentTimeMillis();

        int insertedRecordCount=0;

        switch (args[0].toLowerCase()) {
            case "-download_only":
                download(download, args[1]);
                break;

            case "-process_only":
                filenames.add(args[1]);

                insertedRecordCount = process.processFiles(filenames, false);
                printInteractionCounts();
                break;

            case "-download+process":
                download(download, args[1].toLowerCase());

                insertedRecordCount = process.processFiles(filenames, download.getFailedRequests()==0);
                printInteractionCounts();
                break;
            default:
                printUsageAndExit();
        }

        long time1 = System.currentTimeMillis();
        System.out.println("Inserted Record Count: " + insertedRecordCount);
        System.out.println("PROCESS ELAPSED TIME: "+ Utils.formatElapsedTime(time0, time1));
        System.out.println("--- OK --- pipeline finished normally ---");
    }

    void download(Download download, String species) throws Exception {
        long time0 = System.currentTimeMillis();

        if( getSupportedSpecies().contains(species) ) {
            System.out.println("Downloading " + species + " protein Interactions data to a local file....");
            List<String> all = new ArrayList<>(Arrays.asList(species));
            filenames.add(download.download2File(all, species));
        } else if( species.equals("all") ) {
            System.out.println("Downloading protein interactions data of BELOW SPECIES to a local file... ");
            for(String speciesName: getSupportedSpecies()){
                System.out.print(" "+speciesName);
            }
            System.out.println();

            filenames.add(download.download2File(getSupportedSpecies(), "AllSPECIES"));
        } else {
            printUsageAndExit();
        }

        System.out.println("   ELAPSED TIME: "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        System.out.println("   IMEX API REQUESTS MADE: "+ download.getApiRequestsMade());
        System.out.println("      FAILED API REQUESTS: "+ download.getFailedRequests());
    }

    /**
     * PRINTS THE USAGE AND EXITS WHEN WRONG ARGUMENTS ARE PASSED
     */
    void printUsageAndExit() {
        System.out.println(getVersion());
        System.out.println("Copyright RGD");
        System.out.println("Usage:");
        System.out.println("  java -jar IMEXInteractionsPipeline.jar [-download_only |-process_only | -download+process]");
        System.out.println("    -download_only <species>    -downloads interactions for given species from IMEX databases to a LOCAL FILE and returns LOCAL FILE NAME");
        System.out.println("    -process_only <filename>    -processes the specified file");
        System.out.println("    -download+process <species> -First downloads interactions for given species to a file then processes the downloaded file");
        System.out.println("                                -species [all | rat | mouse | human | dog | bonobo | squirrel | chinchilla]");

        System.exit(-1);
    }

    void printInteractionCounts() throws Exception {
        Dao dao = new Dao();
        for( String speciesCommonName: getSupportedSpecies() ) {
            int speciesTypeKey = SpeciesType.parse(speciesCommonName);
            String species = String.format("%10s", speciesCommonName);
            System.out.println(" Count of interactions for "+species+" = "+dao.getInteractionCountForSpecies(speciesTypeKey));
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setSupportedSpecies(List<String> supportedSpecies) {
        this.supportedSpecies = supportedSpecies;
    }

    public List<String> getSupportedSpecies() {
        return supportedSpecies;
    }
}
