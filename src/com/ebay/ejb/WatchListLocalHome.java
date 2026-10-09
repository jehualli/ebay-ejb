package com.ebay.ejb;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

public interface WatchListLocalHome extends EJBLocalHome {
    WatchListLocal create() throws CreateException;
}
