package edu.mcw.rgd.pipelines.imexinteractions;

import psidev.psi.mi.jami.commons.MIDataSourceOptionFactory;
import psidev.psi.mi.jami.commons.PsiJami;
import psidev.psi.mi.jami.datasource.InteractionStream;
import psidev.psi.mi.jami.factory.MIDataSourceFactory;
import psidev.psi.mi.jami.model.CvTerm;
import psidev.psi.mi.jami.model.Interaction;
import psidev.psi.mi.jami.model.InteractionEvidence;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by mtutaj on 4/9/2019.
 */
public class JamiParser {
    public static void main(String[] args) {

        String fileName = "/tmp/Alliance_molecular_interactions_2.0.mitab";


        //String fileName = "/tmp/physical_interactions_fb.mitab";
        String mitabFileName = "/tmp/out.mitab";
        String xmlFileName = "/tmp/out.xml";

        // initialise default factories for reading and writing MITAB/PSI-MI XML files
        PsiJami.initialiseAllFactories();

        // reading MITAB and PSI-MI XML files

        // the option factory for reading files and other datasources
        MIDataSourceOptionFactory optionfactory = MIDataSourceOptionFactory.getInstance();
        // the datasource factory for reading MITAB/PSI-MI XML files and other datasources
        MIDataSourceFactory dataSourceFactory = MIDataSourceFactory.getInstance();

        // get default options for a file. It will identify if the file is MITAB or PSI-MI XML file and then it will load the appropriate options.
        // By default, the datasource will be streaming (only returns an iterator of interactions), and returns a source of Interaction objects.
        // The default options can be overridden using the optionfactory or by manually adding options listed in MitabDataSourceOptions or PsiXmlDataSourceOptions
        Map<String, Object> parsingOptions = optionfactory.getDefaultMitabOptions(new File(fileName));

        long ie = 0, io = 0;
        int ann = 0, id = 0, rigids = 0, xref = 0;
        InteractionStream interactionSource = null;
        try {
            // Get the stream of interactions knowing the default options for this file
            interactionSource = dataSourceFactory.getInteractionSourceWith(parsingOptions);

            // parse the stream and write as we parse
            // the interactionSource can be null if the file is not recognized or the provided options are not matching any existing/registered datasources
            if (interactionSource != null) {
                Iterator interactionIterator = interactionSource.getInteractionsIterator();

                int u = 0;
                while (interactionIterator.hasNext()) {
                    Interaction interaction = (Interaction) interactionIterator.next();

                    Collection annotations = interaction.getAnnotations();
                    if (annotations.size() > 0) {
                        ann++;
                    }
                    Collection ids = interaction.getIdentifiers();
                    if (ids.size() > 0) {
                        id++;
                    }
                    CvTerm itype = interaction.getInteractionType();
                    Collection participants = interaction.getParticipants();

                    String rigid = interaction.getRigid();
                    if (rigid != null) {
                        rigids++;
                    }
                    Collection xrefs = interaction.getXrefs();
                    if (xrefs.size() > 0) {
                        xref++;
                    }

                    // most of the interactions will have experimental data attached to them so they will be of type InteractionEvidence
                    if (interaction instanceof InteractionEvidence) {
                        InteractionEvidence interactionEvidence = (InteractionEvidence) interaction;
                        // process the interaction evidence
                        System.out.println("IE " + (++ie) + " IMEX " + interactionEvidence.getImexId());
                    } else {
                        System.out.println("IO " + (++io));
                    }

                    if (++u % 1000 == 0) {
                        System.out.println("===");
                    }
                }
            }
        } finally {
            // always close the opened interaction stream
            if (interactionSource != null) {
                interactionSource.close();
            }
        }

        System.out.println("\nIE=" + ie + ", IO=" + io);
        System.out.println("with annotations= " + ann);
        System.out.println("with ids= " + id);
        System.out.println("with rigids= " + rigids);
        System.out.println("with xrefs = " + xref);
    }
}
