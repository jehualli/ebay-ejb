package com.ebay.ejb;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful session bean. Unlike the stateless bean, this instance belongs to
 * one client until it is removed, so its fields are conversational state --
 * the business-tier equivalent of HttpSession, with one difference that
 * matters: the container may passivate it to disk between calls, which is why
 * the lifecycle callbacks below exist and why its state must be serializable.
 */
public class WatchListBean implements SessionBean {

    private SessionContext ctx;
    private List items;
    private int calls;

    public void ejbCreate() {
        items = new ArrayList();
    }

    public void setSessionContext(SessionContext c) { this.ctx = c; }
    public void ejbRemove() { }

    public void ejbActivate() {
        // The container just restored this instance from its passivated form.
        System.out.println("[EJB] WatchListBean activated: "
                           + System.identityHashCode(this));
    }

    public void ejbPassivate() {
        System.out.println("[EJB] WatchListBean passivated: "
                           + System.identityHashCode(this));
    }

    public void add(long itemId) {
        calls++;
        Long key = new Long(itemId);
        if (!items.contains(key)) items.add(key);
    }

    public List getItems() {
        calls++;
        return new ArrayList(items);
    }

    public int getCallCount() { return calls; }

    public String whoAmI() {
        return "stateful instance #" + System.identityHashCode(this)
             + " on thread " + Thread.currentThread().getName();
    }
}
