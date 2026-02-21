package com.aucontraire.gmailbuddy.util;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds RFC 5988 Link headers for pagination.
 * Example output: <https://api.example.com/messages?pageToken=abc>; rel="next"
 */
public class LinkHeaderBuilder {

    private final List<Link> links = new ArrayList<>();

    public LinkHeaderBuilder addNext(String nextPageToken) {
        if (nextPageToken != null && !nextPageToken.isEmpty()) {
            String url = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("pageToken", nextPageToken)
                .build()
                .toUriString();
            links.add(new Link(url, "next"));
        }
        return this;
    }

    public LinkHeaderBuilder addPrev(String prevPageToken) {
        if (prevPageToken != null && !prevPageToken.isEmpty()) {
            String url = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("pageToken", prevPageToken)
                .build()
                .toUriString();
            links.add(new Link(url, "prev"));
        }
        return this;
    }

    public LinkHeaderBuilder addFirst() {
        String url = ServletUriComponentsBuilder.fromCurrentRequest()
            .replaceQueryParam("pageToken")
            .build()
            .toUriString();
        links.add(new Link(url, "first"));
        return this;
    }

    public LinkHeaderBuilder addLast(String lastPageToken) {
        if (lastPageToken != null && !lastPageToken.isEmpty()) {
            String url = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("pageToken", lastPageToken)
                .build()
                .toUriString();
            links.add(new Link(url, "last"));
        }
        return this;
    }

    public String build() {
        if (links.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < links.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Link link = links.get(i);
            sb.append("<").append(link.url).append(">; rel=\"").append(link.rel).append("\"");
        }
        return sb.toString();
    }

    private static class Link {
        final String url;
        final String rel;

        Link(String url, String rel) {
            this.url = url;
            this.rel = rel;
        }
    }
}
