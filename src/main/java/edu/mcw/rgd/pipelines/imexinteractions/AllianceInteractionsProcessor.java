package edu.mcw.rgd.pipelines.imexinteractions;

import edu.mcw.rgd.dao.impl.InteractionAttributesDAO;
import edu.mcw.rgd.dao.impl.InteractionsDAO;
import edu.mcw.rgd.datamodel.Interaction;
import edu.mcw.rgd.datamodel.InteractionAttribute;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Created by mtutaj on 4/1/2019.
 */
public class AllianceInteractionsProcessor {

    Dao dao = new Dao();
    private InteractionsDAO idao= new InteractionsDAO();
    private InteractionAttributesDAO adao= new InteractionAttributesDAO();
    int newAttrs = 0;
    int sameAttrs = 0;
    int newInt = 0;
    int sameInt = 0;
    BufferedWriter newInter;

    public static void main(String[] args) throws Exception {
        String fileName = "/tmp/Alliance_molecular_interactions_2.0.mitab";
        AllianceInteractionsProcessor manager = new AllianceInteractionsProcessor();
        manager.newInter = new BufferedWriter(new FileWriter("new_interactions_from_agr.txt"));

        try {
            // split input file into multiple files: each 50000 rows
            List<String> fileNames = splitInputFile(fileName, 21002);
            Collections.shuffle(fileNames);

            int flybaseLines = 0;
            int wormbaseLines = 0;
            int dataLines = 0;
            for( String fname: fileNames ) {
                System.out.println("FILE "+fname);

                // many flybase lines cause parsing problems: filter them out from original file
                String fileName2 = fname.replace(".mitab", "_lean.mitab");
                String line;
                BufferedWriter out = new BufferedWriter(new FileWriter(fileName2));
                BufferedReader in = Utils.openReader(fname);
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("wormbase:WB")) {
                        wormbaseLines++;
                    } else if (line.startsWith("flybase:FB")) {
                        flybaseLines++;
                    } else {
                        out.write(line);
                        out.write("\n");
                        if( !line.startsWith("#") ) {
                            dataLines++;
                        }
                    }
                }
                in.close();
                out.close();
                System.out.println("lines filtered out (flybase) : " + flybaseLines);
                System.out.println("lines filtered out (wormbase): " + wormbaseLines);

                System.out.println("   data lines = "+dataLines );
                manager.run(fileName2);
                manager.newInter.flush();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        manager.newInter.close();
    }

    static List<String> splitInputFile(String inputFile, int maxLinesPerFile) throws IOException {

        // split input file into multiple files
        List<String> fileNames = new ArrayList<>();

        String outFileNamePrefix = "/tmp/Alliance_molecular_interactions_2.0_part";
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

        for(Interaction pi: piList){
            qc(pi);
        }

        System.out.println(fileName+" OK   INTERACTIONS (new="+newInt+", same="+sameInt+"),   ATTRS (new="+newAttrs+", same="+sameAttrs+")");
        System.out.println();
    }

    public void qc(Interaction pi) throws  Exception{
        int key = idao.getInteractionKey(pi);

        // interaction already in RGD
        if(key!=0){
            sameInt++;
            pi.setInteractionKey(key);
            qcAttributes(pi);
        } else {

            // new interaction
            newInt++;
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
                sameAttrs++;
            } else {
                newAttrs++;
            }
        }
    }

}
