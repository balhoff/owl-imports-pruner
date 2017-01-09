package org.geneontology.pruner;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveImport;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import com.google.common.base.Optional;

public class OWLImportsPruner {

	private OWLDataFactory factory = OWLManager.getOWLDataFactory();
	public static OWLObjectProperty imports = OWLManager.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://geneontology.org/owl-ontology-pruner#imports"));
	public static OWLObjectProperty shouldImport = OWLManager.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://geneontology.org/owl-ontology-pruner#shouldImport"));
	/**
	 * Property relating a "stand-in" version of an ontology to the "full" version. 
	 * E.g. <http://purl.obolibrary.org/obo/go/extensions/pato_import.owl> standsInFor <http://purl.obolibrary.org/obo/pato.owl>
	 */
	public static IRI standsInFor = IRI.create("http://geneontology.org/owl-ontology-pruner#standsInFor");
	/**
	 * Property relating a richer version of an ontology to the standard version. 
	 * E.g. <http://purl.obolibrary.org/obo/go/extensions/go-plus.owl> replacementFor <http://purl.obolibrary.org/obo/go.owl>
	 */
	public static IRI replacementFor = IRI.create("http://geneontology.org/owl-ontology-pruner#replacementFor");
	public static OWLClass loadedOntology = OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://geneontology.org/owl-ontology-pruner#LoadedOntology"));
	public static OWLClass replacedOntology = OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://geneontology.org/owl-ontology-pruner#ReplacedOntology"));

	/**
	 * Inspect ontology IRIs and generate standsInFor assertions based on regex.
	 * Looks for .../(ont)_import.owl patterns to link as stand in for obo:ont.owl
	 * @return ontology of `standsInFor` object property assertions to pass to pruneImports
	 */
	public OWLOntology guessReplacements(OWLOntologyManager manager) throws OWLOntologyCreationException {
		Set<OWLAxiom> axioms = new HashSet<>();
		Pattern regex = Pattern.compile(".+/([^\\s/]+)_import.owl");
		for (OWLOntology ont : manager.getOntologies()) {
			Optional<IRI> iriOpt = ont.getOntologyID().getOntologyIRI();
			if (iriOpt.isPresent()) {
				IRI iri = iriOpt.get();
				Matcher matcher = regex.matcher(iri.toString());
				if (matcher.find()) {
					String match = matcher.group(1);
					if (match != null) {
						axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
								factory.getOWLObjectProperty(standsInFor),
								factory.getOWLNamedIndividual(iri),
								factory.getOWLNamedIndividual(IRI.create("http://purl.obolibrary.org/obo/" + match + ".owl"))));
					}
				}
			}
		}
		OWLOntologyManager replacementsManager = OWLManager.createOWLOntologyManager();
		return replacementsManager.createOntology(axioms);
	}

	/**
	 * Inspect loaded ontologies, use ontology annotations for `standsInFor` and `replacementFor` to compute
	 * preferred imports. Rewrite ontology imports graph using preferred imports.
	 * @param manager containing ontologies to be inspected
	 * @throws OWLOntologyCreationException
	 */
	public void pruneImports(OWLOntologyManager manager) throws OWLOntologyCreationException {
		pruneImports(manager, OWLManager.createOWLOntologyManager().createOntology());
	}

	/**
	 * Inspect loaded ontologies, use ontology annotations for `standsInFor` and `replacementFor` to compute
	 * preferred imports. Rewrite ontology imports graph using preferred imports.
	 * @param manager containing ontologies to be inspected
	 * @param assertedReplacements ontology of object property assertions relating replacement ontologies (with 
	 * ontology IRIs punned as individuals)
	 * @throws OWLOntologyCreationException
	 */
	public void pruneImports(OWLOntologyManager manager, OWLOntology assertedReplacements) throws OWLOntologyCreationException {
		OWLOntologyManager replacementManager = OWLManager.createOWLOntologyManager();
		OWLOntology replacementGraphOnt = replacementManager.loadOntologyFromOntologyDocument(
				new StreamDocumentSource(this.getClass().getResourceAsStream("imports-pruner.ofn")));
		replacementManager.addAxioms(replacementGraphOnt, assertedReplacements.getAxioms(Imports.INCLUDED));
		for (OWLOntology ont : manager.getOntologies()) {
			replacementManager.addAxioms(replacementGraphOnt, extractRelationships(ont));
		}
		OWLReasoner reasoner = new ReasonerFactory().createReasoner(replacementGraphOnt);
		Set<OWLNamedIndividual> replacedOntologies = reasoner.getInstances(replacedOntology, false).getFlattened();
		for (OWLOntology ont : manager.getOntologies()) {
			Optional<IRI> iriOpt = ont.getOntologyID().getOntologyIRI();
			if (iriOpt.isPresent()) {
				IRI iri = iriOpt.get();
				OWLNamedIndividual ontInd = factory.getOWLNamedIndividual(iri);
				Set<OWLNamedIndividual> shouldImports = new HashSet<>(reasoner.getObjectPropertyValues(ontInd, shouldImport).getFlattened());
				shouldImports.removeAll(replacedOntologies);
				for (OWLImportsDeclaration declaration : ont.getImportsDeclarations()) {
					manager.applyChange(new RemoveImport(ont, declaration));
				}
				for (OWLNamedIndividual newImport : shouldImports) {
					OWLImportsDeclaration declaration = factory.getOWLImportsDeclaration(newImport.getIRI());
					manager.applyChange(new AddImport(ont, declaration));
				}
			}
		}
	}

	/**
	 * Convert ontology annotations to replacements object property assertions
	 * @param ont
	 * @return object property assertion axioms
	 */
	public Set<OWLAxiom> extractRelationships(OWLOntology ont) {
		Set<OWLAxiom> axioms = new HashSet<>();
		OWLObjectProperty standsInForOP = factory.getOWLObjectProperty(standsInFor);
		OWLObjectProperty replacementForOP = factory.getOWLObjectProperty(replacementFor);
		Optional<IRI> iriOpt = ont.getOntologyID().getOntologyIRI();
		if (iriOpt.isPresent()) {
			IRI iri = iriOpt.get();
			OWLNamedIndividual ontInd = factory.getOWLNamedIndividual(iri);
			axioms.add(factory.getOWLClassAssertionAxiom(loadedOntology, ontInd));
			for (OWLOntology imported : ont.getDirectImports()) {
				Optional<IRI> importIRIOpt = imported.getOntologyID().getOntologyIRI();
				if (importIRIOpt.isPresent()) {
					OWLNamedIndividual importedOnt = factory.getOWLNamedIndividual(importIRIOpt.get());
					axioms.add(factory.getOWLObjectPropertyAssertionAxiom(imports, ontInd, importedOnt));
				}
			}
			for (OWLAnnotation annotation : ont.getAnnotations()) {
				if (annotation.getProperty().getIRI().equals(standsInFor)) {
					Optional<IRI> iriValueOpt = annotation.getValue().asIRI();
					if (iriValueOpt.isPresent()) {
						IRI iriValue = iriValueOpt.get();
						axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
								standsInForOP, 
								factory.getOWLNamedIndividual(iri), 
								factory.getOWLNamedIndividual(iriValue)));
					}
				}
				if (annotation.getProperty().getIRI().equals(replacementFor)) {
					Optional<IRI> iriValueOpt = annotation.getValue().asIRI();
					if (iriValueOpt.isPresent()) {
						IRI iriValue = iriValueOpt.get();
						axioms.add(factory.getOWLObjectPropertyAssertionAxiom(
								replacementForOP, 
								factory.getOWLNamedIndividual(iri), 
								factory.getOWLNamedIndividual(iriValue)));
					}
				}
			}
		}
		return axioms;
	}

}

