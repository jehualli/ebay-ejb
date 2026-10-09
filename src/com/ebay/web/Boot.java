package com.ebay.web;

import com.ebay.core.AuctionCloser;
import com.ebay.core.AuctionHouse;
import com.ebay.core.SeedData;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/** Builds the marketplace at deploy time and starts the auction clock. */
public class Boot implements ServletContextListener {

    static final String HOUSE  = "ebay.house";
    static final String CLOSER = "ebay.closer";

    public void contextInitialized(ServletContextEvent e) {
        ServletContext ctx = e.getServletContext();

        AuctionHouse house = new AuctionHouse();
        AuctionCloser closer = new AuctionCloser(house);
        SeedData.load(house, closer);
        closer.start();

        ctx.setAttribute(HOUSE, house);
        AuctionHouse.setCurrent(house);   // visible to the EJB container too
        ctx.setAttribute(CLOSER, closer);
        ctx.log("eBay online: " + house.countUsers() + " members, "
                + house.countOpen() + " live auctions.");
    }

    public void contextDestroyed(ServletContextEvent e) {
        AuctionCloser c = (AuctionCloser) e.getServletContext().getAttribute(CLOSER);
        if (c != null) c.stop();
    }

    public static AuctionHouse house(ServletContext ctx) {
        return (AuctionHouse) ctx.getAttribute(HOUSE);
    }
}
