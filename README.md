# eBay, 1999 — EJB edition — a Model 2 servlet + JSP application

> **This is a copy of `~/ebay-jsp` with an EJB tier added**, so the two can be
> diffed. The original is unchanged and still runs on port 8081; this one runs
> on **8084**. See *What changed* at the bottom.


An auction site built the way J2EE web applications were built when JSP was
new: **servlet controllers, JavaBean model, JSP 1.2 views, and a custom tag
library**, wired by a hand-written Servlet 2.3 `web.xml`.

![the front page](docs/home.png)

## Run it

```sh
./run.sh                 # docker build + docker run
open http://localhost:8081/
```

Sign in as **guest / ebay**, or register a new User ID. Every seeded member
also uses the password `ebay`, so you can sign in as `beaniequeen99` to see a
seller's view or `sniper_steve` to bid against yourself.

```sh
docker logs -f ebay      # Tomcat's log
docker rm -f ebay        # stop
```

The marketplace opens with 43 auctions across 18 members — Beanie Babies, a
1st Edition Charizard, a Bondi Blue iMac, a Furby, and somebody's Y2K survival
kit. Some listings end within minutes of boot, so auctions close and winners
are declared while you watch.

## Proxy bidding, which is the whole point

You enter the most you will pay. The site bids for you, raising by the
smallest increment needed to keep you in the lead, and stops at your maximum.
The displayed price is therefore driven by the **loser's** ceiling, not the
winner's.

Verified against the running app:

| Step | What happens | Price | High bidder |
|---|---|---|---|
| Opening | listing starts at $10.00 | $10.00 | — |
| guest enters a **$50.00** maximum | price does not move — nobody to outbid | $10.00 | guest |
| sniper_steve enters **$45.00** | runs into guest's hidden proxy and is outbid instantly | **$46.00** | guest |
| sniper_steve returns with **$60.00** | takes the lead, but only pays one increment over guest's $50 | **$51.00** | sniper_steve |

The bid history records both halves of that middle step — the challenger's
$45.00 and the standing proxy's automatic $46.00 answer. That cascade of bids
placed by nobody is what a real eBay history looked like.

Increments follow eBay's published table ($0.05 under a dollar, rising to
$100 over $5,000), reserve prices keep an item from selling below a secret
floor, and a background `Timer` closes auctions and lets the seeded members
snipe in the last three minutes.

## Why this app, and not the Napster one

`~/napster-servlet` is a desktop-client simulation: real-time progress that
needs client-pull hacks, dense grids assembled in Java, almost no page flow.
JSP's home turf is the opposite — forms that can fail, multi-step flow, and
paginated tables. Nearly every screen here is a form that can be rejected:

- **Bidding** is two steps — enter a maximum, review a page that spells out
  the commitment, confirm. Every failure (below minimum, own item, auction
  closed, unparseable amount) lands back on the review page with the message
  and the amount still in the box.
- **Sell Your Item** validates seven fields and redisplays the whole form
  with the member's input intact, then redirects on success so a refresh
  cannot list the item twice.
- **Browse and Search** are one controller behind two URL patterns, rendering
  one view, paginated 25 to a page.

## The JSP 1.2 toolkit, and only that

`web.xml` declares the Servlet 2.3 DTD, and that choice has teeth: Jasper
compiles the pages as JSP 1.2, so `${...}` expression language is **off**.
Drop `${1 + 1}` into a page and it renders literally, because EL did not
arrive until JSP 2.0 in 2003. The container enforces the period.

So the pages use what 1999–2001 actually had:

| Technique | Where |
|---|---|
| `<%@ include file %>` — static, spliced in before compiling | `header.jsp` / `footer.jsp`, sharing the caller's locals |
| `<jsp:include>` + `<jsp:param>` — runtime, its own servlet | `itemrows.jsp`, which renders any list named by a param |
| `<jsp:forward>` | `index.jsp`, the welcome file, dispatching to the home controller |
| `<%@ page isErrorPage %>` + `exception` | `error.jsp`, the `<error-page>` target |
| Scriptlets and expressions | everywhere a value or a loop is needed |

### The tag library

`WEB-INF/ebay.tld` declares seven tags, each a handler class extending
`TagSupport` with a setter per attribute. This is the part a pure-servlet app
cannot demonstrate:

| Tag | Does |
|---|---|
| `<ebay:money cents="…"/>` | long cents → `$1,234.56` |
| `<ebay:timeLeft end="…"/>` | countdown, turning red inside the last hour |
| `<ebay:feedback user="…"/>` | nick + score + star in its colour tier, linked to the profile |
| `<ebay:esc value="…"/>` | HTML escaping — what `c:out` did from 2002 |
| `<ebay:pageLinks page pages base/>` | `< Previous 1 [2] 3 Next >` |
| `<ebay:loggedIn>` / `<ebay:anonymous>` | conditional body tags — `EVAL_BODY_INCLUDE` or `SKIP_BODY` |

The star tiers are eBay's own: yellow at 10, blue at 50, turquoise at 100,
purple at 500, red at 1,000, green at 5,000, shooting star at 10,000.

Handlers are pooled and reused by the container, so each one clears its
attributes in `doEndTag()` — a detail that bit everybody at least once.

## Layout

```
src/com/ebay/model/   Item (the proxy-bid state machine), Bid, User, Category, Feedback
src/com/ebay/core/    AuctionHouse, AuctionCloser (the clock), Money, Stars, Text, SeedData
src/com/ebay/web/     BaseController + one controller per use case, Boot, SignInFilter
src/com/ebay/tags/    seven TagSupport handlers
webapp/WEB-INF/web.xml   Servlet 2.3 descriptor — element order fixed by the DTD
webapp/WEB-INF/ebay.tld  the tag library descriptor
webapp/WEB-INF/jsp/      the views, under WEB-INF so no one can request one directly
webapp/index.jsp         welcome file, <jsp:forward> to /home
Dockerfile               tomcat:9.0-jdk17 — javac against Tomcat's own jars, no Maven
```

Tomcat 9 is the last release line implementing `javax.servlet` (Tomcat 10
moved to `jakarta.servlet`), and it ships Jasper, so the JSPs compile on first
request exactly as they did then.

State is in memory: restart the container and it is late 1999 again.
Timestamps are shifted so the current year prints as `99`.

**This is a simulation for demonstrating 1999-era technique. It is not
affiliated with eBay Inc., nothing is for sale, and no money changes hands.**


---

## What changed (EJB edition)

The same application, with a second container underneath it. `~/ebay-jsp` runs
on Tomcat — a servlet container. This copy runs on **TomEE 8 "plus"**, which is
Tomcat plus **OpenEJB**, so both containers are present in one deployment and
the same work can be done down either path.

Era note: the app's `web.xml` declares Servlet 2.3, which is J2EE 1.3, whose
EJB specification is **EJB 2.0** (August 2001). The beans are written in that
style — `SessionBean` with lifecycle callbacks, local home and component
interfaces, and a 2.0 DTD `ejb-jar.xml`. No annotations: those arrive with
EJB 3.0 in 2006.

### Files added

```
src/com/ebay/ejb/BiddingLocalHome.java     EJB 2.0 local home (the factory)
src/com/ebay/ejb/BiddingLocal.java         local component interface
src/com/ebay/ejb/BiddingBean.java          stateless session bean
src/com/ebay/ejb/WatchListLocalHome.java   home for the stateful bean
src/com/ebay/ejb/WatchListLocal.java       its component interface
src/com/ebay/ejb/WatchListBean.java        stateful session bean
src/com/ebay/web/ContainerDemoController.java   runs the experiments
webapp/WEB-INF/ejb-jar.xml                 the EJB container's descriptor
webapp/WEB-INF/resources.xml               two datasources: JTA and non-JTA
webapp/WEB-INF/jsp/contenedores.jsp        the comparison page
```

### Files modified

| File | Change |
|---|---|
| `Dockerfile` | `tomcat:9` → `tomee:8-jre8-plus`, with a JDK 8 build stage |
| `run.sh` | port 8084, container name `ebay-ejb` |
| `webapp/WEB-INF/web.xml` | added `<resource-ref>`, two `<ejb-local-ref>`, the demo servlet |
| `webapp/WEB-INF/jsp/header.jsp` | nav link to the comparison page |
| `src/com/ebay/core/AuctionHouse.java` | static handle so the bean can reach the market |
| `src/com/ebay/web/Boot.java` | publishes the market to that handle |

Nothing else was touched. Every existing route behaves as before.

### What `/contenedores` demonstrates

Measured live on each request, not described:

1. **Who serves the call.** One servlet instance across all threads, versus a
   pool of bean instances.
2. **The same unsynchronized counter, both tiers.** Eight threads × 500
   increments with no `synchronized` anywhere. The servlet instance field
   loses several hundred updates; the bean loses none, because the container
   never lets two threads into one instance. Each bean call reports the
   counter *before and after its own loop*, so the delta proves it — the
   absolute totals keep climbing across page loads, since pooled instances
   outlive the request. That is precisely why client state must never live in
   a stateless bean.
3. **Two writes, then a failure.** A real HSQLDB table. The bean method is
   declared `Required` and throws after both inserts: the container rolls both
   back, with no rollback code written in Java. The web tier does the same two
   writes on its own non-JTA connection and leaves a half-written record
   behind on every reload.
4. **Conversational state.** `HttpSession` beside a stateful session bean,
   which keeps a stable identity per client and carries activate/passivate
   callbacks.
5. **How the web tier obtains each.** `ctx.getAttribute(...)` versus a JNDI
   lookup through a home interface — with the container-generated proxy class
   names printed, since neither is your class.
6. **What there is to administer** — the management contrast in a table:
   descriptors, deployment unit, concurrency, transactions, security,
   lifecycle, naming, resources, tuning knobs.

### Expected noise in the log

Every load of `/contenedores` logs one `SEVERE` from
`EjbTransactionUtil.handleSystemException` reading *"deliberate failure after
two inserts"*. That is the rollback demonstration working, not a defect.

### Running both at once

```sh
cd ~/ebay-jsp && ./run.sh     # servlet container only, port 8081
cd ~/ebay-ejb && ./run.sh     # servlet + EJB container, port 8084
diff -ru ~/ebay-jsp ~/ebay-ejb
```
