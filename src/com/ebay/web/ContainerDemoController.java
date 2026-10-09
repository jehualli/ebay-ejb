package com.ebay.web;

import com.ebay.ejb.BiddingLocal;
import com.ebay.ejb.BiddingLocalHome;
import com.ebay.ejb.WatchListLocal;
import com.ebay.ejb.WatchListLocalHome;

import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the same work down both tiers and reports what each container did.
 *
 * Everything on the page is measured at request time, not described: instance
 * identities, thread names, a real race, and a real transaction rollback
 * against a real database.
 */
public class ContainerDemoController extends BaseController {

    private static final int THREADS = 8;
    private static final int LOOPS = 500;

    /**
     * A servlet instance field. There is exactly one ContainerDemoController
     * for the whole application, so this is shared by every request thread at
     * once. That is the defining property of the servlet container's model,
     * and the reason the race below happens.
     */
    private int unsafeCounter;

    protected void handle(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        List notes = new ArrayList();
        ensureLedger(notes);

        req.setAttribute("servletIdentity",
            "servlet instance #" + System.identityHashCode(this)
            + " on thread " + Thread.currentThread().getName());

        try {
            InitialContext ic = new InitialContext();
            BiddingLocalHome home =
                (BiddingLocalHome) ic.lookup("java:comp/env/ejb/Bidding");

            req.setAttribute("homeClass", home.getClass().getName());

            // --- 1. who answers, five calls in a row -----------------------
            List whoIs = new ArrayList();
            for (int i = 0; i < 5; i++) {
                BiddingLocal b = home.create();
                whoIs.add(b.whoAmI());
            }
            req.setAttribute("sequentialCalls", whoIs);
            req.setAttribute("proxyClass", home.create().getClass().getName());

            // --- 2. the race ----------------------------------------------
            runRace(req, home);

            // --- 3. the transaction ---------------------------------------
            runTransaction(req, home);

            // --- 4. conversational state ----------------------------------
            runStateful(req, ic);

        } catch (Exception e) {
            req.setAttribute("ejbError", e.toString());
        }

        req.setAttribute("notes", notes);
        forward(req, res, "contenedores.jsp");
    }

    // ------------------------------------------------------------- the race

    private void runRace(HttpServletRequest req, final BiddingLocalHome home)
            throws Exception {

        unsafeCounter = 0;
        Thread[] ts = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            ts[i] = new Thread(new Runnable() {
                public void run() { spinServletField(LOOPS); }
            });
        }
        for (int i = 0; i < THREADS; i++) ts[i].start();
        for (int i = 0; i < THREADS; i++) ts[i].join();
        int servletResult = unsafeCounter;

        final String[] beanResults = new String[THREADS];
        final Exception[] beanError = new Exception[1];
        Thread[] bt = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            final int idx = i;
            bt[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        // Each thread asks the container for a reference; the
                        // pool hands out instances so that no instance is ever
                        // entered by two threads at once.
                        BiddingLocal b = home.create();
                        beanResults[idx] = b.spinAndReport(LOOPS);
                    } catch (Exception e) {
                        beanError[0] = e;
                    }
                }
            });
        }
        for (int i = 0; i < THREADS; i++) bt[i].start();
        for (int i = 0; i < THREADS; i++) bt[i].join();

        // The container's guarantee: one instance is never entered by two
        // threads at once, so no increment is lost. Each call reports the
        // counter before and after its own loop; the difference is exactly
        // LOOPS when nothing interfered. The absolute totals are larger
        // because pooled instances outlive the request.
        boolean beanExact = beanError[0] == null;
        int beanApplied = 0;
        java.util.Set instances = new java.util.LinkedHashSet();
        List shown = new ArrayList();

        for (int i = 0; i < THREADS; i++) {
            String r = beanResults[i];
            if (r == null) { beanExact = false; continue; }
            String[] parts = r.split(":");
            int before = Integer.parseInt(parts[1]);
            int after = Integer.parseInt(parts[2]);
            instances.add(parts[0]);
            beanApplied += (after - before);
            if (after - before != LOOPS) beanExact = false;
            shown.add("+" + (after - before) + " (instance " + parts[0]
                      + " went " + before + "->" + after + ")");
        }
        if (beanApplied != THREADS * LOOPS) beanExact = false;

        req.setAttribute("beanInstances", new Integer(instances.size()));
        req.setAttribute("beanApplied", new Integer(beanApplied));
        req.setAttribute("beanResults", shown);
        req.setAttribute("raceThreads", new Integer(THREADS));
        req.setAttribute("raceLoops", new Integer(LOOPS));
        req.setAttribute("raceExpected", new Integer(THREADS * LOOPS));
        req.setAttribute("raceServlet", new Integer(servletResult));
        req.setAttribute("raceServletLost", new Integer(THREADS * LOOPS - servletResult));
        req.setAttribute("raceBeanExact", Boolean.valueOf(beanExact));
        req.setAttribute("raceBeanError", beanError[0] == null ? null : beanError[0].toString());
    }

    /** Deliberately non-atomic, on a field shared by all request threads. */
    private void spinServletField(int n) {
        for (int i = 0; i < n; i++) {
            int read = unsafeCounter;
            Thread.yield();
            unsafeCounter = read + 1;
        }
    }

    // ------------------------------------------------------ the transaction

    private void runTransaction(HttpServletRequest req, BiddingLocalHome home)
            throws Exception {

        BiddingLocal bean = home.create();

        int before = bean.ledgerCount();
        String ejbOutcome;
        try {
            bean.ledgerWrite(1000000001L, "guest", 12345L, true);
            ejbOutcome = "no exception (unexpected)";
        } catch (Exception e) {
            ejbOutcome = e.getClass().getName();
        }
        int afterEjb = bean.ledgerCount();

        // The web tier doing the same two writes on its own connection.
        int afterWeb = afterEjb;
        String webOutcome;
        Connection con = null;
        try {
            DataSource ds = (DataSource) new InitialContext()
                .lookup("java:comp/env/jdbc/ebayNoTx");
            con = ds.getConnection();
            insert(con, 1000000001L, "guest", 12345L, "BID");
            // ... and the process falls over here, before the second write.
            webOutcome = "failed after the first insert, nothing undid it";
        } catch (Exception e) {
            webOutcome = "error: " + e;
        } finally {
            if (con != null) try { con.close(); } catch (Exception ignored) { }
        }
        afterWeb = bean.ledgerCount();

        req.setAttribute("ledgerBefore", new Integer(before));
        req.setAttribute("ledgerAfterEjb", new Integer(afterEjb));
        req.setAttribute("ledgerAfterWeb", new Integer(afterWeb));
        req.setAttribute("ejbOutcome", ejbOutcome);
        req.setAttribute("webOutcome", webOutcome);
    }

    // --------------------------------------------------- conversational state

    private void runStateful(HttpServletRequest req, InitialContext ic)
            throws Exception {

        HttpSession session = req.getSession(true);
        WatchListLocal watch = (WatchListLocal) session.getAttribute("watchBean");
        if (watch == null) {
            WatchListLocalHome wh =
                (WatchListLocalHome) ic.lookup("java:comp/env/ejb/WatchList");
            watch = wh.create();
            session.setAttribute("watchBean", watch);
        }
        watch.add(1000000001L + watch.getCallCount());

        req.setAttribute("statefulWho", watch.whoAmI());
        req.setAttribute("statefulItems", watch.getItems());
        req.setAttribute("statefulCalls", new Integer(watch.getCallCount()));
        req.setAttribute("httpSessionId", session.getId());
    }

    // ------------------------------------------------------------- plumbing

    private void ensureLedger(List notes) {
        Connection con = null;
        try {
            DataSource ds = (DataSource) new InitialContext()
                .lookup("java:comp/env/jdbc/ebayNoTx");
            con = ds.getConnection();
            Statement st = con.createStatement();
            st.execute("CREATE TABLE IF NOT EXISTS ledger ("
                     + "id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                     + "item_id BIGINT, bidder VARCHAR(40), cents BIGINT,"
                     + "kind VARCHAR(10))");
            st.close();
        } catch (Exception e) {
            notes.add("ledger unavailable: " + e);
        } finally {
            if (con != null) try { con.close(); } catch (Exception ignored) { }
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
}
