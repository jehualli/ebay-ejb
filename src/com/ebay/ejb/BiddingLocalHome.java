package com.ebay.ejb;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

/**
 * EJB 2.0 local home interface: the factory the container implements for you.
 *
 * In the servlet tier you write `new AuctionHouse()`. Here you never call a
 * constructor at all -- you ask the container for a reference and it decides
 * which pooled instance you get.
 */
public interface BiddingLocalHome extends EJBLocalHome {
    BiddingLocal create() throws CreateException;
}
