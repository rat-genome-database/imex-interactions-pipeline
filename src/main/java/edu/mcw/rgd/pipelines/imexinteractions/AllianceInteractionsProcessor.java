package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.dao.impl.InteractionAttributesDAO;
import edu.mcw.rgd.dao.impl.InteractionsDAO;
import edu.mcw.rgd.datamodel.Interaction;
import edu.mcw.rgd.datamodel.InteractionAttribute;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Created by mtutaj on 4/1/2019.
 */
public class AllianceInteractionsProcessor {

    Dao dao = new Dao();
    private InteractionsDAO idao= new InteractionsDAO();
    private InteractionAttributesDAO adao= new InteractionAttributesDAO();
    BufferedWriter newInter;
    CounterPool counters = new CounterPool();

    public static void main(String[] args) throws Exception {
        new AllianceInteractionsProcessor().run();
    }

    void run() throws IOException {
        String fileName = "/tmp/Alliance_molecular_interactions_2.1.mitab";
        newInter = new BufferedWriter(new FileWriter("new_interactions_from_agr.txt"));

        try {
            // split input file into multiple files: each 50000 rows
            List<String> fileNames = splitInputFile(fileName, 1500000);

            fileNames.parallelStream().forEach( fname -> {
                try {
                    // many flybase lines cause parsing problems: filter them out from original file
                    String fileName2 = fname.replace(".mitab", "_lean.mitab");
                    String line;
                    BufferedWriter out = new BufferedWriter(new FileWriter(fileName2));
                    BufferedReader in = Utils.openReader(fname);
                    while ((line = in.readLine()) != null) {
                        out.write(line);
                        out.write("\n");
                        if (!line.startsWith("#")) {
                            counters.increment("data lines");
                        }
                    }
                    in.close();
                    out.close();

                    run(fileName2);
                    newInter.flush();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println(counters.dumpAlphabetically());

        newInter.close();
    }

    static List<String> splitInputFile(String inputFile, int maxLinesPerFile) throws IOException {

        // split input file into multiple files
        List<String> fileNames = new ArrayList<>();

        String outFileNamePrefix = "/tmp/Alliance_molecular_interactions_2.1_part";
        String line, header = "";
        BufferedReader in = Utils.openReader(inputFile);
        while( (line=in.readLine())!=null ) {
            if( line.startsWith("#") ) {
                header += line + "\n";
            } else {
                break;
            }
        }

        String outFileName;
        BufferedWriter out = null;
        int dataLines = 0;
        do {
            if( dataLines%maxLinesPerFile == 0 ) {
                // close previous part file
                if( out!=null ) {
                    out.close();
                }
                // open new part file
                outFileName = outFileNamePrefix+(fileNames.size())+".mitab";
                fileNames.add(outFileName);
                out = new BufferedWriter(new FileWriter(outFileName));
                out.write(header);
            }
            out.write(line);
            out.write("\n");
            dataLines++;
        } while( (line=in.readLine())!=null );

        in.close();
        out.close();

        System.out.println("DATA LINES: "+dataLines);
        return fileNames;
    }

    void run(String fileName) throws Exception {
        Process p = new Process();

        List<Interaction> piList = p.loadProteinInteractions(fileName);

        Collection<Interaction> interactions = p.mergeDuplicateInteractions( piList );

        interactions.parallelStream().forEach( pi -> {
            try {
                qc(pi);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void qc(Interaction pi) throws  Exception{
        int key = idao.getInteractionKey(pi);

        // interaction already in RGD
        if(key!=0){
            counters.increment("   SAME INTERACTIONS");
            pi.setInteractionKey(key);
            qcAttributes(pi);
        } else {

            // new interaction
            counters.increment("   NEW INTERACTIONS");
            counters.add("   NEW ATTRIBUTES NEW INTERACTIONS", pi.getInteractionAttributes().size());
            newInter.write("  RGD:" + pi.getRgdId1() + " - RGD:" + pi.getRgdId2() + "|" + pi.getInteractionType()+dumpAttrs(pi)+"\n");
        }
    }

    String dumpAttrs(Interaction pi) {
        StringBuilder buf = new StringBuilder();
        for( InteractionAttribute ia: pi.getInteractionAttributes() ) {
            if( buf.length()==0 ) {
                buf.append("  ATTRS: ");
            } else {
                buf.append(", ");
            }
            buf.append(ia.getAttributeName()).append(":").append(ia.getAttributeValue());
        }
        return buf.toString();
    }

    public void qcAttributes(Interaction pi) throws Exception {
        List<InteractionAttribute> aList = pi.getInteractionAttributes();
        int key = pi.getInteractionKey();

        for( InteractionAttribute a: aList ) {
            a.setInteractionKey(key);
            int aKey = adao.getAttributeKey(a);
            if(aKey != 0) {
                counters.increment("   SAME ATTRIBUTES");
            } else {
                counters.increment("   NEW ATTRIBUTES SAME INTERACTIONS");
            }
        }
    }

}
