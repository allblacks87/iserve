/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$

    File:        APISpec.java
    Created by:  Dave Reynolds
    Created on:  31 Jan 2010
 */

package com.epimorphics.lda.specs;

import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.bindings.VariableExtractor;
import com.epimorphics.lda.core.ModelLoader;
import com.epimorphics.lda.exceptions.APIException;
import com.epimorphics.lda.exceptions.EldaException;
import com.epimorphics.lda.query.QueryParameter;
import com.epimorphics.lda.renderers.Factories;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.shortnames.StandardShortnameService;
import com.epimorphics.lda.sources.AuthMap;
import com.epimorphics.lda.sources.GetDataSource;
import com.epimorphics.lda.sources.Source;
import com.epimorphics.lda.support.ModelPrefixEditor;
import com.epimorphics.lda.support.RendererFactoriesSpec;
import com.epimorphics.lda.vocabularies.EXTRAS;
import com.epimorphics.util.RDFUtils;
import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.Map1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.epimorphics.util.RDFUtils.getStringValue;

/**
 * Encapsulates a specification of a single API instance.
 * API specification is transported via RDF but this object state
 * is self contained to make it easier to migrate to GAE-JDO
 * storage for persisting specs.
 *
 * @author <a href="mailto:der@epimorphics.com">Dave Reynolds</a>
 * @version $Revision: $
 */
public class APISpec {

    static Logger log = LoggerFactory.getLogger(APISpec.class);

    protected final List<APIEndpointSpec> endpoints = new ArrayList<APIEndpointSpec>();

    protected final PrefixMapping prefixes;
    protected final ShortnameService sns;

    protected final Source dataSource;
    protected final String primaryTopic;
    protected final String specificationURI;
    protected final String defaultLanguage;
    protected final String base;

    public final int defaultPageSize;
    public final int maxPageSize;

    protected final Factories factoryTable;
    protected final boolean hasParameterBasedContentNegotiation;
    protected final List<Source> describeSources;
    public final Bindings bindings = new Bindings();

    public final int describeThreshold;

    public final String cachePolicyName;

    protected final ModelPrefixEditor modelPrefixEditor = new ModelPrefixEditor();

    /**
     * The default number of selected items required for a DESCRIBE
     * query to use nested selects if they are available.
     */
    public static final int DEFAULT_DESCRIBE_THRESHOLD = 10;

    public APISpec(FileManager fm, Resource specification, ModelLoader loader) {
        this(new AuthMap(), fm, specification, loader);
    }

    public APISpec(AuthMap am, FileManager fm, Resource specification, ModelLoader loader) {
        specificationURI = specification.getURI();
        defaultPageSize = RDFUtils.getIntValue(specification, API.defaultPageSize, QueryParameter.DEFAULT_PAGE_SIZE);
        maxPageSize = RDFUtils.getIntValue(specification, API.maxPageSize, QueryParameter.MAX_PAGE_SIZE);
        describeThreshold = RDFUtils.getIntValue(specification, EXTRAS.describeThreshold, DEFAULT_DESCRIBE_THRESHOLD);
        prefixes = ExtractPrefixMapping.from(specification);
        sns = loadShortnames(specification, loader);
        dataSource = GetDataSource.sourceFromSpec(fm, specification, am);
        describeSources = extractDescribeSources(fm, am, specification, dataSource);
        primaryTopic = getStringValue(specification, FOAF.primaryTopic, null);
        defaultLanguage = getStringValue(specification, API.lang, null);
        base = getStringValue(specification, API.base, null);
        bindings.putAll(VariableExtractor.findAndBindVariables(specification));
        factoryTable = RendererFactoriesSpec.createFactoryTable(specification);
        hasParameterBasedContentNegotiation = specification.hasProperty(API.contentNegotiation, API.parameterBased);
        cachePolicyName = getStringValue(specification, EXTRAS.cachePolicyName, "default");
        extractEndpointSpecifications(specification);
        extractModelPrefixEditor(specification);
    }

    private void extractModelPrefixEditor(Resource specification) {
        StmtIterator eps = specification.listProperties(EXTRAS.rewriteResultURIs);
        while (eps.hasNext()) extractSingleModelprefixFromTo(eps.next());
    }

    private void extractSingleModelprefixFromTo(Statement s) {
        Resource S = s.getSubject();
        if (s.getObject().isLiteral())
            throw new EldaException("Object of editPrefix property of " + S + " is a literal.");
        Resource edit = s.getResource();
        String from = getStringValue(edit, EXTRAS.ifStarts);
        if (from == null) throw new EldaException("Missing from for " + S);
        String to = getStringValue(edit, EXTRAS.replaceStartBy);
        if (to == null) throw new EldaException("Missing elda:to for " + S);
        modelPrefixEditor.set(from, to);
    }

    private StandardShortnameService loadShortnames(Resource specification, ModelLoader loader) {
        return new StandardShortnameService(specification, prefixes, loader);
    }

    /**
     * Answer the list of sources that may be used to enhance the view of
     * the selected items. Always contains at least the given source.
     */
    private List<Source> extractDescribeSources(FileManager fm, AuthMap am, Resource specification, Source dataSource) {
        List<Source> result = new ArrayList<Source>();
        result.add(dataSource);
        result.addAll(specification.listProperties(EXTRAS.enhanceViewWith).mapWith(toSource(fm, am)).toList());
        return result;
    }

    private static final Map1<Statement, Source> toSource(final FileManager fm, final AuthMap am) {
        return new Map1<Statement, Source>() {
            @Override
            public Source map1(Statement o) {
                return GetDataSource.sourceFromSpec(fm, o.getResource(), am);
            }
        };
    }

    ;

    private void extractEndpointSpecifications(Resource specification) {
        NodeIterator ni = specification.getModel().listObjectsOfProperty(specification, API.endpoint);
        while (ni.hasNext()) {
            RDFNode n = ni.next();
            if (!(n instanceof Resource)) {
                throw new APIException("Bad specification file, non-resource definition of Endpoint. " + n);
            }
            Resource endpoint = (Resource) n;
            // endpoints.add( new APIEndpointSpec( this, this, endpoint ) );
            endpoints.add(getAPIEndpointSpec(endpoint));
        }
    }

    /**
     * Return the prefix mapping, applies to whole API
     */
    public PrefixMapping getPrefixMap() {
        return prefixes;
    }

    /**
     * Return a utility for mapping names to short names as
     * configured for this API.
     */
    public ShortnameService getShortnameService() {
        return sns;
    }

    /**
     * Return list of individual instances which make up this API.
     */
    public List<APIEndpointSpec> getEndpoints() {
        return endpoints;
    }

    /**
     * Return the data source (remote or local) which this
     * API wraps.
     */
    public Source getDataSource() {
        return dataSource;
    }

    /**
     * Return the primary topic of this list/set, or null if none is specified
     */
    public String getPrimaryTopic() {
        return primaryTopic;
    }

    /**
     * The URI for the RDF resource which specifies this API
     */
    public String getSpecURI() {
        return specificationURI;
    }

    /**
     * The default language for encoding plain literals (null if no default).
     */
    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    /**
     * Printable representation for debugging
     */
    @Override
    public String toString() {
        return "API-" + specificationURI;
    }

    public List<Source> getDescribeSources() {
        return describeSources;
    }

    /**
     * Answer the bindings of variables for this API configuration.
     * Never null, but may be empty.
     */
    public Bindings getBindings() {
        return bindings;
    }

    /**
     * Answer the value of api:base for this configuration, or null if
     * no api:base was provided.
     */
    public String getBase() {
        return base;
    }

    /**
     * Answer a copy of the renderer factory map. (It's a copy so
     * that the caller can freely mutate it afterwards.)
     */
    public Factories getRendererFactoryTable() {
        return factoryTable.copy();
    }

    public String getCachePolicyName() {
        return cachePolicyName;
    }

    /**
     * Returns a new APIEndpointSpec for this APISpec and the given endpoint
     */
    protected APIEndpointSpec getAPIEndpointSpec(Resource endpoint) {
        return new APIEndpointSpec(this, this, endpoint);
    }

    public boolean hasParameterBasedContentNegotiation() {
        return hasParameterBasedContentNegotiation;
    }

    public ModelPrefixEditor getModelPrefixEditor() {
        return modelPrefixEditor;
    }
}

