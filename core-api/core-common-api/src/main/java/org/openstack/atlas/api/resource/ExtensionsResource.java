package org.openstack.atlas.api.resource;

import org.openstack.atlas.api.config.ConfigHelper;
import org.openstack.atlas.api.response.ResponseFactory;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.ws.rs.GET;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
@Scope("request")
public class ExtensionsResource {

    @GET
    public Response retrieveExtensions() {
        try {
            Document root = readFileToDom("extensions.xml");
            root = addExtensions(root);
            String xmlString = documentToString(root);
            return Response.status(Response.Status.OK).entity(xmlString).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseFactory.getErrorResponse(e);
        }
    }

    private Document addExtensions(Document root) {
        List<String> enabledExtensions = ConfigHelper.getExtensionPrefixesFromConfiguration();

        if (enabledExtensions.isEmpty()) return root;

        List<Document> extensions = new ArrayList<Document>();
        ConfigurationBuilder configBuilder = new ConfigurationBuilder();

        for (String enabledExtension : enabledExtensions) {
            configBuilder.addUrls(ClasspathHelper.forPackage("org.openstack.atlas." + enabledExtension + ".extensions"));
        }

        Reflections reflections = new Reflections(configBuilder.setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner()));

        Set<String> xmlFiles = reflections.getResources(Pattern.compile("extension.xml"));

        for (String xmlFile : xmlFiles) {
            extensions.add(readFileToDom(xmlFile));
        }

        for (Document extension : extensions) {
            try {
                Node extensionNode = root.importNode(extension.getDocumentElement(), true);
                root.getDocumentElement().appendChild(extensionNode);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return root;
    }

    private String documentToString(Document document) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
        LSSerializer writer = impl.createLSSerializer();
        return writer.writeToString(document);
    }

    public Document readFileToDom(String file) {
        try {
            final InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(file);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();

            System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

            return doc;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getTagValue(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag).item(0).getChildNodes();
        Node nValue = nlList.item(0);
        return nValue.getNodeValue();
    }
}
