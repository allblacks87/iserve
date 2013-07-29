package es.usc.citius.composit.importer.wsc.wscxml;


import es.usc.citius.composit.importer.wsc.wscxml.model.XMLInstance;
import es.usc.citius.composit.importer.wsc.wscxml.model.services.XMLService;
import es.usc.citius.composit.importer.wsc.wscxml.model.services.XMLServices;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.open.kmi.iserve.commons.io.ServiceTransformer;
import uk.ac.open.kmi.iserve.commons.model.MessageContent;
import uk.ac.open.kmi.iserve.commons.model.Operation;
import uk.ac.open.kmi.iserve.commons.model.Resource;
import uk.ac.open.kmi.iserve.commons.model.Service;


import javax.xml.bind.JAXB;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Imports and transforms datasets from the Web Service Challenge 2008 (XML format)
 * Date: 7/18/13
 * @author Pablo Rodríguez Mier
 */
public class WSCImporter implements ServiceTransformer {

    private static final Logger log = LoggerFactory.getLogger(WSCImporter.class);
    private WSCXMLSemanticReasoner reasoner;
    private String xmlTaxonomyURL;
    private String owlOntologyURL;
    public static final String mediaType = "text/xml";

    /**
     * The WSC specification does not define the ontology URI of the concepts.
     * Thus, the real URI of the ontology should be provided using the config.properties
     */
    public WSCImporter() throws IOException, ConfigurationException {
        // NOTE: The inputs and outputs of the services should be automatically translated from instances to
        // concepts using the XML Reasoner
        PropertiesConfiguration props = new PropertiesConfiguration("config.properties");
        this.xmlTaxonomyURL = (String) props.getProperty("taxonomy.url");
        log.info("Using taxonomy {}", this.xmlTaxonomyURL);
        this.owlOntologyURL = (String) props.getProperty("ontology.url");
        URL ontology = new URL(xmlTaxonomyURL);
        log.info("Using ontology {}", this.owlOntologyURL);
        this.reasoner = new WSCXMLSemanticReasoner(ontology.openStream());
        // TODO Convert the ontology to OWL ??
    }


    private MessageContent transform(XMLInstance instance, String baseURI){
        // TODO Handle baseURI in some way!
        String concept = this.reasoner.getConceptInstance(instance.getName());
        URI uri = URI.create(this.owlOntologyURL + "#" + concept);
        MessageContent content = new MessageContent(uri);
        content.setSource(uri);
        content.setWsdlGrounding(uri);
        content.setLabel(instance.getName());
        content.addModelReference(new Resource(uri));
        return content;
    }

    @Override
    public List<Service> transform(InputStream originalDescription, String baseUri) {
        // De-serialize from XML
        XMLServices services = JAXB.unmarshal(originalDescription, XMLServices.class);
        List<Service> listServices = new ArrayList<Service>(services.getServices().size());
        String uri = baseUri.endsWith("#") ? baseUri : baseUri + "#";
        // Create the services following the iserve-commons-vocabulary model
        for(XMLService service : services.getServices()){
            URI srvURI = URI.create(uri+service.getName());
            log.debug("Transforming {}", srvURI);
            Service modelService = new Service(srvURI);
            modelService.setSource(srvURI);
            modelService.setWsdlGrounding(srvURI);

            Operation operation = new Operation(URI.create(baseUri + service.getName()));
            for(XMLInstance input : service.getInputs().getInstances()){
                operation.addInput(transform(input, baseUri));
            }
            for(XMLInstance output : service.getOutputs().getInstances()){
                operation.addOutput(transform(output, baseUri));
            }
            modelService.addOperation(operation);
            modelService.setLabel(service.getName());
            listServices.add(modelService);
        }
        return listServices;
    }

    @Override
    public String getSupportedMediaType() {
        return mediaType;
    }

    @Override
    public List<String> getSupportedFileExtensions() {
        return Arrays.asList("xml");
    }

    @Override
    public String getVersion() {
        return "WSC Importer 0.1";
    }


    // TODO Create a proper test
    public static void main(String[] args) throws IOException, ConfigurationException {
        BasicConfigurator.configure();
        WSCImporter imp = new WSCImporter();
        System.out.println("Args: " + args);
        File f = new File("/home/citius/Dropbox/PFC-TESIS/Estancias/KMi/iserve-project/http-server/htdocs/services/services.xml");
        imp.transform(new FileInputStream(f),f.toURI().toString());
    }
}
