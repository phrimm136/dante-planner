package org.danteplanner.backend.shared.freshness;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

public class FreshnessStampFilter extends OncePerRequestFilter {

    private final GtidWriteCapture writeCapture;
    private final MarkerStamp markerStamp;

    public FreshnessStampFilter(GtidWriteCapture writeCapture, MarkerStamp markerStamp) {
        this.writeCapture = writeCapture;
        this.markerStamp = markerStamp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        writeCapture.begin();
        FreshnessDeclarations.begin();
        ChosenReadSource.clear();
        ReadSourceSelector.clearSkips();
        try {
            filterChain.doFilter(request, response);
            // A bodied response is already committed by the converter's flush.
            markerStamp.stamp();
        } finally {
            writeCapture.clear();
            FreshnessDeclarations.clear();
            ChosenReadSource.clear();
            ReadSourceSelector.clearSkips();
        }
    }
}
