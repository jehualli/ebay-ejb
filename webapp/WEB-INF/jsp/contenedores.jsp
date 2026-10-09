<% String pageTitle = "Contenedores"; %>
<%@ include file="header.jsp" %>
<%
    List notes = (List) request.getAttribute("notes");
    String ejbError = (String) request.getAttribute("ejbError");
%>

<FONT SIZE="4"><B>Servlet container vs. EJB container</B></FONT><BR>
<FONT SIZE="2" COLOR="#666666">Everything below is measured on this request.
Reload the page and the numbers change.</FONT>
<BR><BR>

<% if (ejbError != null) { %>
  <TABLE WIDTH="100%" CELLPADDING="6" CELLSPACING="0" BORDER="0" CLASS="err">
  <TR><TD><FONT SIZE="2"><B>EJB tier error:</B> <ebay:esc value="<%= ejbError %>"/></FONT></TD></TR>
  </TABLE><BR>
<% } %>

<!-- ================= 1. instances and threads ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="2">1 &#149; Who serves the call</TD></TR>
<TR BGCOLOR="#FFFFFF" VALIGN="TOP">
  <TD WIDTH="50%"><FONT SIZE="2"><B>Servlet container</B><BR>
    <FONT SIZE="1">One instance for the whole application. Every request
    thread runs through the same object, which is why a field on a servlet is
    shared state.</FONT><BR><BR>
    <TT><FONT SIZE="1"><%= request.getAttribute("servletIdentity") %></FONT></TT>
  </TD>
  <TD WIDTH="50%"><FONT SIZE="2"><B>EJB container</B><BR>
    <FONT SIZE="1">A pool of instances. Five calls in a row, showing which
    instance the container handed out each time:</FONT><BR><BR>
    <TT><FONT SIZE="1">
    <%
      List calls = (List) request.getAttribute("sequentialCalls");
      if (calls != null) for (int i = 0; i < calls.size(); i++) {
    %>
      <%= calls.get(i) %><BR>
    <% } %>
    </FONT></TT>
  </TD>
</TR>
</TABLE>
<BR>

<!-- ================= 2. the race ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="2">2 &#149; The same unsynchronized counter, both tiers</TD></TR>
<TR BGCOLOR="#FFFFFF"><TD COLSPAN="2"><FONT SIZE="1">
  <%= request.getAttribute("raceThreads") %> threads &#215;
  <%= request.getAttribute("raceLoops") %> increments, with no
  <TT>synchronized</TT> anywhere in either tier. Expected total:
  <B><%= request.getAttribute("raceExpected") %></B>.
</FONT></TD></TR>
<TR BGCOLOR="#FFFFFF" VALIGN="TOP">
  <TD WIDTH="50%"><FONT SIZE="2">
    Incrementing a <B>servlet instance field</B>:<BR><BR>
    <FONT SIZE="4" COLOR="#CC0000"><B><%= request.getAttribute("raceServlet") %></B></FONT>
    <FONT SIZE="1"> of <%= request.getAttribute("raceExpected") %>
    &#151; <B><%= request.getAttribute("raceServletLost") %> updates lost</B></FONT>
    <BR><BR><FONT SIZE="1">All eight threads shared one field. You own the lock.</FONT>
  </TD>
  <TD WIDTH="50%"><FONT SIZE="2">
    Incrementing a <B>bean instance field</B>:<BR><BR>
    <%
      Boolean exact = (Boolean) request.getAttribute("raceBeanExact");
      if (exact != null && exact.booleanValue()) {
    %>
      <FONT SIZE="4" COLOR="#008000"><B><%= request.getAttribute("beanApplied") %></B></FONT>
      <FONT SIZE="1"> of <%= request.getAttribute("raceExpected") %>
      &#151; <B>none lost</B></FONT>
      <BR><BR><FONT SIZE="1">Served by
      <B><%= request.getAttribute("beanInstances") %></B> pooled instance(s).
      Each call applied exactly <%= request.getAttribute("raceLoops") %>
      increments to whichever instance served it. The before/after totals are
      larger than that because a pooled instance keeps its fields between
      calls and even between requests &#151; which is why client state must
      never live in a stateless bean. Reload and watch them climb.<BR>
      <TT><%
        List br = (List) request.getAttribute("beanResults");
        if (br != null) for (int i = 0; i < br.size(); i++) {
      %><%= br.get(i) %>&nbsp; <% } %></TT></FONT>
    <% } else { %>
      <FONT SIZE="2" COLOR="#CC0000">not exact:
      <%= request.getAttribute("raceBeanError") %></FONT>
    <% } %>
    <BR><BR><FONT SIZE="1">The container guarantees one thread per instance,
    so instance state needs no lock. <B>Static</B> fields on a bean are still
    shared &#151; the guarantee is about instances, not classes.</FONT>
  </TD>
</TR>
</TABLE>
<BR>

<!-- ================= 3. transactions ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="2">3 &#149; Two writes, then a failure</TD></TR>
<TR BGCOLOR="#FFFFFF"><TD COLSPAN="2"><FONT SIZE="1">
  A real HSQLDB table. Each tier writes a BID row and an AUDIT row and then
  fails between them. Rows in the ledger before the experiment:
  <B><%= request.getAttribute("ledgerBefore") %></B>.
</FONT></TD></TR>
<TR BGCOLOR="#FFFFFF" VALIGN="TOP">
  <TD WIDTH="50%"><FONT SIZE="2"><B>EJB container</B><BR>
    <FONT SIZE="1">Method declared
    <TT>&lt;trans-attribute&gt;Required&lt;/trans-attribute&gt;</TT>. The bean
    throws after both inserts; no rollback code exists anywhere in Java.</FONT>
    <BR><BR>
    <FONT SIZE="1">Threw: <TT><%= request.getAttribute("ejbOutcome") %></TT><BR>
    Ledger rows after: </FONT>
    <FONT SIZE="3" COLOR="#008000"><B><%= request.getAttribute("ledgerAfterEjb") %></B></FONT>
    <FONT SIZE="1"> &#151; both inserts rolled back by the container</FONT>
  </TD>
  <TD WIDTH="50%"><FONT SIZE="2"><B>Servlet container</B><BR>
    <FONT SIZE="1">Its own connection, no transaction. The first insert
    committed before the failure and nothing undoes it.</FONT>
    <BR><BR>
    <FONT SIZE="1"><%= request.getAttribute("webOutcome") %><BR>
    Ledger rows after: </FONT>
    <FONT SIZE="3" COLOR="#CC0000"><B><%= request.getAttribute("ledgerAfterWeb") %></B></FONT>
    <FONT SIZE="1"> &#151; a half-written record, left behind on every reload</FONT>
  </TD>
</TR>
</TABLE>
<BR>

<!-- ================= 4. conversational state ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="2">4 &#149; State that belongs to one client</TD></TR>
<TR BGCOLOR="#FFFFFF" VALIGN="TOP">
  <TD WIDTH="50%"><FONT SIZE="2"><B>HttpSession</B> (servlet container)<BR>
    <FONT SIZE="1">Keyed by a cookie, held by the web tier, thrown away on
    timeout.</FONT><BR><BR>
    <TT><FONT SIZE="1">id = <%= request.getAttribute("httpSessionId") %></FONT></TT>
  </TD>
  <TD WIDTH="50%"><FONT SIZE="2"><B>Stateful session bean</B> (EJB container)<BR>
    <FONT SIZE="1">One instance dedicated to this client. Its identity stays
    the same across reloads, and the container may passivate it to disk between
    calls &#151; which is why it has activate and passivate callbacks.</FONT>
    <BR><BR>
    <TT><FONT SIZE="1"><%= request.getAttribute("statefulWho") %><BR>
    calls so far: <%= request.getAttribute("statefulCalls") %><BR>
    watching: <%= request.getAttribute("statefulItems") %></FONT></TT>
  </TD>
</TR>
</TABLE>
<BR>

<!-- ================= 5. how you get hold of it ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="2">5 &#149; How the web tier obtains each one</TD></TR>
<TR BGCOLOR="#FFFFFF" VALIGN="TOP">
  <TD WIDTH="50%"><FONT SIZE="2"><B>Servlet container</B><BR><BR>
    <TT><FONT SIZE="1">AuctionHouse house =<BR>
    &nbsp;&nbsp;(AuctionHouse) ctx.getAttribute("ebay.house");</FONT></TT>
    <BR><BR><FONT SIZE="1">You built it, you hold it, you know its class.</FONT>
  </TD>
  <TD WIDTH="50%"><FONT SIZE="2"><B>EJB container</B><BR><BR>
    <TT><FONT SIZE="1">
    InitialContext ic = new InitialContext();<BR>
    BiddingLocalHome home = (BiddingLocalHome)<BR>
    &nbsp;&nbsp;ic.lookup("java:comp/env/ejb/Bidding");<BR>
    BiddingLocal bean = home.create();
    </FONT></TT>
    <BR><BR><FONT SIZE="1">
    home is <TT><%= request.getAttribute("homeClass") %></TT><BR>
    bean is <TT><%= request.getAttribute("proxyClass") %></TT><BR>
    Neither is your class. Both are generated, and the transaction and
    security checks live in them.</FONT>
  </TD>
</TR>
</TABLE>
<BR>

<!-- ================= 6. what you administer ================= -->
<TABLE WIDTH="100%" CELLPADDING="4" CELLSPACING="1" BORDER="0" BGCOLOR="#CCCCCC">
<TR CLASS="hdr"><TD COLSPAN="3">6 &#149; What there is to administer</TD></TR>
<TR CLASS="hdr"><TD WIDTH="26%">&nbsp;</TD><TD WIDTH="37%">Servlet container</TD><TD WIDTH="37%">EJB container</TD></TR>
<%
  String[][] admin = {
    {"Descriptor", "web.xml", "ejb-jar.xml (+ resources.xml for the pools)"},
    {"Unit of deployment", "WAR", "ejb-jar, normally inside an EAR"},
    {"Concurrency", "you write synchronized", "container: one thread per instance"},
    {"Transactions", "you call commit and rollback", "declared per method in the descriptor"},
    {"Security", "a filter you write", "&lt;method-permission&gt; and roles"},
    {"Lifecycle", "init and destroy", "create, activate, passivate, remove, pooling"},
    {"Naming", "none", "a JNDI namespace per component"},
    {"Resources", "you open connections", "container pools and enlists them"},
    {"Tuning knobs", "thread pool, session timeout", "pool sizes, timeouts, transaction manager, realms"},
  };
  for (int i = 0; i < admin.length; i++) {
%>
<TR BGCOLOR="<%= i % 2 == 0 ? "#FFFFFF" : "#F4F4F4" %>" VALIGN="TOP">
  <TD><FONT SIZE="1"><B><%= admin[i][0] %></B></FONT></TD>
  <TD><FONT SIZE="1"><%= admin[i][1] %></FONT></TD>
  <TD><FONT SIZE="1"><%= admin[i][2] %></FONT></TD>
</TR>
<% } %>
</TABLE>

<%
  if (notes != null && !notes.isEmpty()) {
%>
<BR><FONT SIZE="1" COLOR="#CC0000">
<% for (int i = 0; i < notes.size(); i++) { %>
  <%= notes.get(i) %><BR>
<% } %>
</FONT>
<% } %>

<%@ include file="footer.jsp" %>
