package com.ebay.ejb;

import javax.ejb.EJBLocalObject;

/**
 * EJB 2.0 local component interface. The servlet never touches BiddingBean;
 * it holds a container-generated proxy implementing this interface, which is
 * where transactions and security are applied before your code runs.
 */
public interface BiddingLocal extends EJBLocalObject {

    /** Which pooled instance answered, and on which thread. */
    String whoAmI();

    /**
     * Increments an instance field n times with no synchronization, and
     * reports "instanceHash:before:after".
     *
     * after - before is exactly n when no other thread touched this
     * instance, which is the container's real guarantee. The absolute total
     * is NOT n: a pooled instance keeps its fields between calls and even
     * between requests, which is exactly why client state must never live
     * in a stateless bean.
     */
    String spinAndReport(int n);

    /**
     * Writes two rows to the ledger and then fails if asked to.
     * Declared Required in ejb-jar.xml, so the container owns the outcome.
     */
    int ledgerWrite(long itemId, String bidder, long cents, boolean fail);

    int ledgerCount();

    /** The real business operation, moved out of the web tier. */
    String placeBid(String bidder, long itemId, String amount);
}
