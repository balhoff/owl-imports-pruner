package org.geneontology.pruner;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class Demo {

	public static void main(String[] args) throws OWLOntologyCreationException {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology uberonExt = manager.loadOntology(IRI.create("http://purl.obolibrary.org/obo/uberon/ext.owl"));
		OWLOntology goPlus = manager.loadOntology(IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-plus.owl"));
		manager.loadOntology(IRI.create("http://purl.obolibrary.org/obo/pato.owl"));
		printImports(uberonExt);
		printImports(goPlus);

		OWLDataFactory factory = OWLManager.getOWLDataFactory();
		OWLObjectProperty replacementFor = factory.getOWLObjectProperty(OWLImportsPruner.replacementFor);
		OWLOntologyManager repManager = OWLManager.createOWLOntologyManager();
		OWLOntology replacements = repManager.createOntology();
		repManager.addAxiom(replacements, factory.getOWLObjectPropertyAssertionAxiom(
				replacementFor, 
				factory.getOWLNamedIndividual(IRI.create("http://purl.obolibrary.org/obo/uberon/ext.owl")), 
				factory.getOWLNamedIndividual(IRI.create("http://purl.obolibrary.org/obo/uberon.owl"))));
		repManager.addAxiom(replacements, factory.getOWLObjectPropertyAssertionAxiom(
				replacementFor, 
				factory.getOWLNamedIndividual(IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-plus.owl")), 
				factory.getOWLNamedIndividual(IRI.create("http://purl.obolibrary.org/obo/go.owl"))));
		OWLImportsPruner pruner = new OWLImportsPruner();
		manager.addAxioms(replacements, pruner.guessReplacements(manager).getAxioms());
		pruner.pruneImports(manager, replacements);
		printImports(uberonExt);
		printImports(goPlus);
	}

	private static void printImports(OWLOntology ont) {
		System.out.println("Ontology: " + ont.getOntologyID());
		for (OWLOntology importOnt : ont.getOWLOntologyManager().getDirectImports(ont)) {
			System.out.println("    imports: " + importOnt.getOntologyID());
		}
	}

}
