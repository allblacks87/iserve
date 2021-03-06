/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/
package com.epimorphics.lda.restlets;

import com.epimorphics.lda.cache.Cache;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/control/show-cache")
public class ShowCache {

    @GET
    @Produces("text/html")
    public Response clearCache() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head></head><body style='background-color: #aaffaa'>\n");
        sb.append("<h1>reset cache counts</h1>\n");
        sb.append("<form method='POST' action='reset-counts'><input type='SUBMIT' value='RESET'></form>\n");
        sb.append("<h1>cache state</h1>\n");
        Cache.Registry.showAll(sb);
        sb.append("</body></html>\n");
        return RouterRestlet.returnAs(sb.toString(), "text/html");
    }
}
