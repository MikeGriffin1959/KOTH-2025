<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" errorPage="error.jsp" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <title>Home</title> 
    <title>KOTH Edge — Week ${week}</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">


    <style>
        /* namespaced so this can't collide with global styles */
        #edgePage { font-family: Arial, Helvetica, sans-serif; max-width: 1100px; margin: 20px auto; padding: 0 16px; color: #1a1a1a; }
        #edgePage h1 { font-size: 22px; margin-bottom: 4px; }
        #edgePage .sub { color: #555; font-size: 13px; margin-bottom: 16px; }
        #edgePage .controls { margin: 12px 0 18px; font-size: 13px; }
        #edgePage .controls a { text-decoration: none; padding: 4px 10px; border: 1px solid #ccc; border-radius: 4px; margin-right: 6px; color: #1a1a1a; }
        #edgePage .controls a.active { background: #1f3a5f; color: #fff; border-color: #1f3a5f; }
        #edgePage table { width: 100%; border-collapse: collapse; font-size: 13px; }
        #edgePage th, #edgePage td { padding: 7px 9px; border-bottom: 1px solid #e3e3e3; text-align: center; }
        #edgePage th { background: #1f3a5f; color: #fff; font-weight: 600; position: sticky; top: 0; }
        #edgePage td.team { text-align: left; font-weight: 600; }
        #edgePage td.opp  { text-align: left; color: #555; }
        #edgePage tr.rec { background: #eaf4ea; }
        #edgePage tr.rec td.team::after { content: " ★"; color: #2e7d32; }
        #edgePage .lives { font-weight: 700; color: #2e7d32; }
        #edgePage .flag { display: inline-block; background: #fdecea; color: #b3261e; border-radius: 3px; padding: 1px 6px; margin: 1px; font-size: 11px; }
        #edgePage .flag.div  { background: #fff4e5; color: #9a6700; }
        #edgePage .flag.thin { background: #eef0f2; color: #555; }
        #edgePage .pct { font-variant-numeric: tabular-nums; }
        #edgePage .safety { font-weight: 700; }
        #edgePage .note { font-size: 12px; color: #777; margin-top: 14px; line-height: 1.5; }
        #edgePage .muted { color: #aaa; }
    </style>
</head>
<body>
  <div class="container">
       <!-- Header Section -->
       <jsp:include page="header.jsp">
           <jsp:param name="pageTitle" value="Home" />
       </jsp:include>

	<div id="edgePage">
	
	    <h1>KOTH Edge Advisor</h1>
	    <div class="sub">
	        Season ${season} &middot; Week ${week} &middot; allocating <strong>${lives}</strong>
	        life<c:if test="${lives != 1}">s</c:if> &middot; mode: <strong>${alloc}</strong>
	    </div>
	
	    <div class="controls">
	        Allocation:
	        <a href="?season=${season}&week=${week}&lives=${lives}&alloc=SPREAD"
	           class="${alloc == 'SPREAD' ? 'active' : ''}">Spread</a>
	        <a href="?season=${season}&week=${week}&lives=${lives}&alloc=STACK"
	           class="${alloc == 'STACK' ? 'active' : ''}">Stack</a>
	    </div>
	
	    <c:choose>
	        <c:when test="${empty candidates}">
	            <p class="muted">No candidates for this week. Run the edge build first
	            (<code>/admin/edge/run?season=${season}&amp;week=${week}</code>), then reload.</p>
	        </c:when>
	        <c:otherwise>
	            <table>
	                <thead>
	                <tr>
	                    <th style="text-align:left;">Pick</th>
	                    <th style="text-align:left;">Opponent</th>
	                    <th>Site</th>
	                    <th>Market</th>
	                    <th>FPI</th>
	                    <th>ELO</th>
	                    <th>Blended</th>
	                    <th>Safety</th>
	                    <th>Flags</th>
	                    <th>Lives</th>
	                </tr>
	                </thead>
	                <tbody>
	                <c:forEach var="c" items="${candidates}">
	                    <tr class="${c.allocatedLives > 0 ? 'rec' : ''}">
	                        <td class="team">${c.teamName}</td>
	                        <td class="opp">${c.home ? 'vs' : '@'} ${c.opponentName}</td>
	                        <td>${c.home ? 'Home' : 'Away'}</td>
	                        <td class="pct">
	                            <c:choose>
	                                <c:when test="${c.marketProb != null}"><fmt:formatNumber value="${c.marketProb * 100}" maxFractionDigits="1"/>%</c:when>
	                                <c:otherwise><span class="muted">—</span></c:otherwise>
	                            </c:choose>
	                        </td>
	                        <td class="pct">
	                            <c:choose>
	                                <c:when test="${c.fpiProb != null}"><fmt:formatNumber value="${c.fpiProb * 100}" maxFractionDigits="1"/>%</c:when>
	                                <c:otherwise><span class="muted">—</span></c:otherwise>
	                            </c:choose>
	                        </td>
	                        <td class="pct">
	                            <c:choose>
	                                <c:when test="${c.eloProb != null}"><fmt:formatNumber value="${c.eloProb * 100}" maxFractionDigits="1"/>%</c:when>
	                                <c:otherwise><span class="muted">—</span></c:otherwise>
	                            </c:choose>
	                        </td>
	                        <td class="pct"><strong><fmt:formatNumber value="${c.blendedProb * 100}" maxFractionDigits="1"/>%</strong></td>
	                        <td class="pct safety"><fmt:formatNumber value="${c.safetyScore * 100}" maxFractionDigits="1"/></td>
	                        <td>
	                            <c:forEach var="f" items="${c.upsetFlags}">
	                                <c:set var="cls" value="flag" />
	                                <c:if test="${fn:startsWith(f, 'DIVISIONAL')}"><c:set var="cls" value="flag div" /></c:if>
	                                <c:if test="${fn:startsWith(f, 'THIN')}"><c:set var="cls" value="flag thin" /></c:if>
	                                <span class="${cls}">${f}</span>
	                            </c:forEach>
	                        </td>
	                        <td><c:if test="${c.allocatedLives > 0}"><span class="lives">${c.allocatedLives}</span></c:if></td>
	                    </tr>
	                </c:forEach>
	                </tbody>
	            </table>
	
	            <div class="note">
	                <strong>How to read this:</strong> Safety score = blended win probability minus penalties
	                for model/market divergence and situational upset risk. The list is ranked by safety, not raw
	                probability &mdash; so a clean favorite can outrank a higher-probability team that carries flags.
	                Highlighted rows (★) are where your lives are allocated.
	                This is a read-only preview; applying picks comes later.
	            </div>
	        </c:otherwise>
	    </c:choose>
	
	</div>
</div>

<%@ include file="footer.jsp" %>
</body>
</html>
