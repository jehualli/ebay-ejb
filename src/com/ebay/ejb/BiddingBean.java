package com.ebay.ejb;

import com.ebay.core.AuctionHouse;
import com.ebay.core.BidResult;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Stateless session bean, EJB 2.0 style: implements SessionBean, provides
 * ejbCreate(), and lets the container drive every lifecycle callback.
 *
 * Note what is NOT here, compared with the servlet tier doing the same work:
 *
 *   - no `synchronized`. The container guarantees that one bean instance is
 *     never entered by two threads at once, so instance fields are safe.
 *     (Static fields are NOT -- those are shared, exactly as in a servlet.)
 *   - no commit or rollback. The transaction attribute in ejb-jar.xml says
 *     Required; throwing a system exception is how you vote to roll back.
 *   - no security check. <method-permission> in the descriptor does it.
 */
public class BiddingBean implements SessionBean {

    private SessionContext ctx;

    /** Instance state. Safe without a lock, because of the container's contract. */
    private int counter;

    public void ejbCreate() { }
    public void setSessionContext(SessionContext c) { this.ctx = c; }
    public void ejbRemove() { }
    public void ejbActivate() { }
    public void ejbPassivate() { }

    public String whoAmI() {
        return "bean instance #" + System.identityHashCode(this)
             + " on thread " + Thread.currentThread().getName();
    }

    public String spinAndReport(int n) {
        int before = counter;
        for (int i = 0; i < n; i++) {
            int read = counter;      // deliberately non-atomic
            Thread.yield();
            counter = read + 1;
        }
        return System.identityHashCode(this) + ":" + before + ":" + counter;
    }

    // ------------------------------------------------------------ ledger

    public int ledgerWrite(long itemId, String bidder, long cents, boolean fail) {
        Connection con = null;
        try {
            DataSource ds = (DataSource) new InitialContext()
                .lookup("java:comp/env/jdbc/ebayDS");
            con = ds.getConnection();

            insert(con, itemId, bidder, cents, "BID");
            insert(con, itemId, bidder, cents, "AUDIT");

            if (fail) {
                // A system exception is the EJB 2.0 way of saying "undo it".
                // Both inserts disappear; no rollback code is written here.
                throw new EJBException("deliberate failure after two inserts");
            }
            return 2;
        } catch (EJBException e) {
            throw e;
        } catch (Exception e) {
            throw new EJBException(e);
        } finally {
            close(con);
        }
    }

    public int ledgerCount() {
        Connection con = null;
        try {
            DataSource ds = (DataSource) new InitialContext()
                .lookup("java:comp/env/jdbc/ebayDS");
            con = ds.getConnection();
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ledger");
            rs.next();
            int n = rs.getInt(1);
            rs.close();
            st.close();
            return n;
        } catch (Exception e) {
            throw new EJBException(e);
        } finally {
            close(con);
        }
    }

    private void insert(Connection con, long itemId, String bidder, long cents,
                        String kind) throws Exception {
        PreparedStatement ps = con.prepareStatement(
            "INSERT INTO ledger (item_id, bidder, cents, kind) VALUES (?,?,?,?)");
        ps.setLong(1, itemId);
        ps.setString(2, bidder);
        ps.setLong(3, cents);
        ps.setString(4, kind);
        ps.executeUpdate();
        ps.close();
    }

    private void close(Connection con) {
        if (con != null) {
            try { con.close(); } catch (Exception ignored) { }
        }
    }

    // ----------------------------------------------------------- business

    /**
     * The seam between the two tiers. AuctionHouse still holds the market in
     * memory and is reached through a static handle, because a bean has no
     * ServletContext -- which is itself part of the lesson: the business tier
     * is not allowed to know it is being called by a web tier.
     */
    public String placeBid(String bidder, long itemId, String amount) {
        AuctionHouse house = AuctionHouse.current();
        if (house == null) return "the auction house is not running";
        BidResult r = house.placeBid(bidder, itemId, amount);
        return r.getMessage();
    }
}
