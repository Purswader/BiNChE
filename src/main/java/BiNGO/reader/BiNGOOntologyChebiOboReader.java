/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BiNGO.reader;

import cytoscape.data.annotation.ChEBIOntologyTerm;
import cytoscape.data.annotation.Ontology;
import cytoscape.data.annotation.OntologyTerm;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the ChEBI Ontology Obo file. It requires InChIs to asses whether an entry is a molecule or not.
 * 
 * @author pmoreno
 */
public class BiNGOOntologyChebiOboReader extends BiNGOOntologyOboReader {

    /**
     * Initializes the reader with the given ChEBI obo file.
     * 
     * @param chebiOboFile
     * @param namespace
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws Exception 
     */
    public BiNGOOntologyChebiOboReader(File chebiOboFile,
                                       String namespace) throws IllegalArgumentException, IOException, Exception {

        this(chebiOboFile.getPath(), namespace);
    }

    /**
     * Initializes the reader with the given ChEBI obo file name (path).
     * @param chebiOboFileName
     * @param namespace
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws Exception 
     */
    public BiNGOOntologyChebiOboReader(String chebiOboFileName,
                                       String namespace) throws IllegalArgumentException, IOException, Exception {

        super(chebiOboFileName, namespace);
    }

    @Override
    protected int parseHeader() throws Exception {

        int i = 0;
        while (!lines[i].trim().equals("[Term]")) {
            i++;
        }
        curator = "unknown";
        ontologyType = "unknown";
        return i;

    } 

    @Override
    protected void parse(int c) throws Exception {

        ontology = new Ontology(curator, ontologyType);
        fullOntology = new Ontology(curator, ontologyType);
        int i = c;
        while (i < lines.length && !lines[i].trim().equals("[Typedef]")) {
            i++;
            String name = new String();
            String id = new String();
            // TODO we can remove this set
            Set<String> namespaceSet = new HashSet<String>();
            Set<String> alt_id = new HashSet<String>();
            Set<String> is_a = new HashSet<String>();            
            Set<String> has_part = new HashSet<String>();
            Set<String> has_role = new HashSet<String>();
            boolean obsolete = false;
            boolean molecule=false;
            // TODO we should change this for a buffered strategy.
            while (i < lines.length && !lines[i].trim().equals("[Term]") && !lines[i].trim().equals("[Typedef]") && !lines[i].trim().startsWith("!")) {
                if (!lines[i].trim().isEmpty()) {
                    String ref = lines[i].substring(0, lines[i].indexOf(":")).trim();
                    String value = lines[i].substring(lines[i].indexOf(":") + 1).trim();
                    if (ref.equals("name")) {
                        name = value.trim();
                    } else if (ref.equals("namespace")) {
                        namespaceSet.add(value.trim());
                    } else if (ref.equals("subset")) {
                        namespaceSet.add(value.trim());
                    } else if (ref.equals("id")) {
                        id = value.trim().substring(value.indexOf(":") + 1);
                    } else if (ref.equals("alt_id")) {
                        alt_id.add(value.trim().substring(value.indexOf(":") + 1));
                    } else if (ref.equals("is_a")) {
                        is_a.add(value.split("!")[0].trim().substring(value.indexOf(":") + 1));
                    } else if (ref.equals("relationship")) {
                        if (value.startsWith("has_part")) {
                            has_part.add(value.substring(value.indexOf(":") + 1));
                        } else if (value.startsWith("has_role")) {
                            has_role.add(value.substring(value.indexOf(":") + 1));
                        }
                    } else if (ref.equals("is_obsolete")) {
                        if (value.trim().equals("true")) {
                            obsolete = true;
                        }
                    } else if (ref.equals("synonym") && value.contains("RELATED InChI")) {
                        molecule=true;
                    }
                }
                i++;
            }
            if (obsolete == false && !id.isEmpty()) {                
                // For the ChEBI namespace
                Integer id2 = new Integer(id);
                synonymHash.put(id2, id2);
                ChEBIOntologyTerm term;
                if (!ontology.containsTerm(id2)) {
                    term = new ChEBIOntologyTerm(name, id2);
                    ontology.add(term);
                    fullOntology.add(term);
                } else {
                    term = (ChEBIOntologyTerm)ontology.getTerm(id2);
                }
                term.setMolecule(molecule);
                for (String s : alt_id) {
                    synonymHash.put(new Integer(s), id2);
                }
                for (String s : is_a) {
                    term.addParent(new Integer(s));
                }
                for (String s : has_role) {
                    term.addContainer(new Integer(s));
                }
                for (String s : has_part) { 
                    // elements in has part
                    // are sub parts of the term that we are looking
                    // for. Hence, we get the "smaller" term and
                    // add the current term as a container for it.
                    Integer containedID = new Integer(s);
                    ChEBIOntologyTerm containedTerm;
                    if (ontology.containsTerm(containedID)) {
                        containedTerm = (ChEBIOntologyTerm)ontology.getTerm(containedID);
                    } else {
                        containedTerm = new ChEBIOntologyTerm(name, containedID);
                        ontology.add(containedTerm);
                        fullOntology.add(containedTerm);
                    }

                    containedTerm.addContainer(term.getId());
                }

                /*} else {
                    Integer id2 = new Integer(id);
                    OntologyTerm term = new OntologyTerm(name, id2);
                    if (!fullOntology.containsTerm(id2)) {
                        fullOntology.add(term);
                        for (String s : is_a) {
                            term.addParent(new Integer(s));
                        }
                        //for (String s : part_of) {
                        //    term.addContainer(new Integer(s));
                        //}
                    }
                }*/
                //}
            }
        }

        //explicitly reroute all connections (parent-child relationships) that are missing in subontologies like GOSlim
        //avoid transitive connections
        // TODO This is an undesired bias towards gene ontology.
        /*if (!namespace.equals("biological_process") && !namespace.equals("molecular_function") && !namespace.equals("cellular_component") && !namespace.equals(BingoAlgorithm.NONE)) {
            for (Integer j : (Set<Integer>) ontology.getTerms().keySet()) {
                OntologyTerm o = ontology.getTerm(j);
                HashSet<OntologyTerm> ancestors = findNearestAncestors(new HashSet<OntologyTerm>(), j);
                HashSet<OntologyTerm> prunedAncestors = new HashSet<OntologyTerm>(ancestors);
                for (OntologyTerm o2 : ancestors) {
                    HashSet<OntologyTerm> o2Ancestors = getAllAncestors(new HashSet<OntologyTerm>(), o2);
                    for (OntologyTerm o3 : o2Ancestors) {
                        if (ancestors.contains(o3)) {
                            System.out.println("removed " + o3.getName());
                            prunedAncestors.remove(o3);
                        }
                    }
                }
                for (OntologyTerm o2 : prunedAncestors) {
                    o.addParent(o2.getId());
                }
            }
        }*/

//        makeOntologyFile(System.getProperty("user.home"));

    } // read
}
