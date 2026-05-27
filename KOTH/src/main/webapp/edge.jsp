<%@ page contentType="text/html;charset=UTF-8" language="java" pageEncoding="UTF-8" errorPage="error.jsp" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH Edge — Week ${week}</title>

    <%-- If your other pages use a shared CSS, link it here to inherit site styling.
         Adjust the path to match your project (e.g. /css/styles.css). --%>
    <%-- <link rel="stylesheet" href="${pageContext.request.contextPath}/css/styles.css"> --%>

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
        #edgePage .crisk { display:inline-block; border-radius:3px; padding:1px 6px; font-size:11px; font-weight:600; }
        #edgePage .crisk.low  { background:#eaf4ea; color:#2e7d32; }
        #edgePage .crisk.med  { background:#fff4e5; color:#9a6700; }
        #edgePage .crisk.high { background:#fdecea; color:#b3261e; }
        #edgePage .veto { display:inline-block; background:#fdecea; color:#b3261e; border-radius:3px; padding:1px 6px; font-size:11px; font-weight:700; margin-left:6px; }
        #edgePage .banner { padding:10px 14px; border-radius:4px; margin:10px 0; font-size:13px; }
        #edgePage .banner.ok   { background:#eaf4ea; color:#1b5e20; border:1px solid #c8e6c9; }
        #edgePage .banner.err  { background:#fdecea; color:#7f1d1d; border:1px solid #f5c2c2; }
        #edgePage .apply-bar { margin:14px 0 6px; display:flex; align-items:center; gap:14px; }
        #edgePage .apply-btn { background:#2e7d32; color:#fff; border:0; padding:9px 18px; border-radius:4px; font-size:14px; font-weight:600; cursor:pointer; }
        #edgePage .apply-btn:disabled { background:#999; cursor:not-allowed; }
        #edgePage .warn-text { color:#9a6700; font-size:12px; }
        #edgePage td.cb { width:30px; }
    </style>
</head>
<body>

<%-- If your other pages include a header/nav, include it here to match the site.
     <jsp:include page="/WEB-INF/views/header.jsp" /> --%>

<div id="edgePage">

    <h1>KOTH Edge Advisor</h1>
    <div class="sub">
        Season ${season} &middot; Week ${week} &middot; allocating <strong>${lives}</strong>
        life<c:if test="${lives != 1}">s</c:if> &middot; mode: <strong>${alloc}</strong>
    </div>

    <div class="controls">
        Allocation:
        <a href="?season=${season}&week=${week}&lives=${lives}&alloc=SPREAD&triage=${triage}"
           class="${alloc == 'SPREAD' ? 'active' : ''}">Spread</a>
        <a href="?season=${season}&week=${week}&lives=${lives}&alloc=STACK&triage=${triage}"
           class="${alloc == 'STACK' ? 'active' : ''}">Stack</a>
        &nbsp;&nbsp;|&nbsp;&nbsp;
        <a href="?season=${season}&week=${week}&lives=${lives}&alloc=${alloc}&triage=true"
           class="${triage ? 'active' : ''}">Run Claude Triage</a>
    </div>

    <c:if test="${not empty applyMessage}">
        <div class="banner ok"><c:out value="${applyMessage}"/></div>
    </c:if>
    <c:if test="${not empty applyError}">
        <div class="banner err"><c:out value="${applyError}"/></div>
    </c:if>

    <c:choose>
        <c:when test="${empty candidates}">
            <p class="muted">No candidates for this week. Run the edge build first
            (<code>/admin/edge/run?season=${season}&amp;week=${week}</code>), then reload.</p>
        </c:when>
        <c:otherwise>
            <form id="edgeApplyForm" method="post" action="${pageContext.request.contextPath}/admin/edge/apply">
                <input type="hidden" name="season" value="${season}"/>
                <input type="hidden" name="week" value="${week}"/>

                <table>
                <thead>
                <tr>
                    <th class="cb"></th>
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
                    <c:if test="${triage}">
                        <th>Claude</th>
                        <th style="text-align:left;">Claude's Read</th>
                    </c:if>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="c" items="${candidates}">
                    <c:set var="isVeto" value="${triage and c.claudeRecommend eq false}"/>
                    <tr class="${c.allocatedLives > 0 ? 'rec' : ''}">
                        <td class="cb">
                            <input type="checkbox" name="pick"
                                   value="${c.espnEventId}|${c.teamName}"
                                   data-team="${c.teamName}"
                                   data-veto="${isVeto ? 'true' : 'false'}"
                                   ${c.allocatedLives > 0 ? 'checked' : ''} />
                        </td>
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
                        <c:if test="${triage}">
                            <td class="pct">
                                <c:choose>
                                    <c:when test="${c.claudeConfidence != null}">
                                        <span class="crisk ${c.claudeUpsetRisk}">
                                            <fmt:formatNumber value="${c.claudeConfidence * 100}" maxFractionDigits="0"/>%
                                            <c:if test="${not empty c.claudeUpsetRisk}">&middot; ${c.claudeUpsetRisk}</c:if>
                                        </span>
                                        <c:if test="${c.claudeRecommend eq false}">
                                            <span class="veto">AVOID</span>
                                        </c:if>
                                    </c:when>
                                    <c:otherwise><span class="muted">—</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td style="text-align:left; color:#444; font-size:12px;">
                                <c:out value="${c.claudeRationale}"/>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>

            <div class="apply-bar">
                <button type="submit" class="apply-btn" id="applyBtn">Apply Selected Picks</button>
                <span class="warn-text">
                    Applying replaces <strong>all</strong> of your Week ${week} picks.
                </span>
            </div>
            </form>

            <div class="note">
                <strong>How to read this:</strong> Safety score = blended win probability minus penalties
                for model/market divergence and situational upset risk. The list is ranked by safety, not raw
                probability &mdash; so a clean favorite can outrank a higher-probability team that carries flags.
                Highlighted rows (★) are where your lives are allocated.
                Checkboxes are pre-selected to the allocation; uncheck or check rows to override before applying.
            </div>

            <script>
            (function() {
                var form = document.getElementById('edgeApplyForm');
                if (!form) return;
                form.addEventListener('submit', function(ev) {
                    var checked = form.querySelectorAll('input[name="pick"]:checked');
                    if (checked.length === 0) {
                        ev.preventDefault();
                        alert('No picks selected.');
                        return;
                    }
                    var vetoed = [];
                    for (var i = 0; i < checked.length; i++) {
                        if (checked[i].getAttribute('data-veto') === 'true') {
                            vetoed.push(checked[i].getAttribute('data-team'));
                        }
                    }
                    if (vetoed.length > 0) {
                        var ok = confirm('Claude flagged these picks as AVOID:\n\n  ' +
                                         vetoed.join(', ') +
                                         '\n\nApply anyway?');
                        if (!ok) { ev.preventDefault(); return; }
                        // attach confirmedVetoes hidden inputs so the server accepts them
                        for (var k = 0; k < checked.length; k++) {
                            if (checked[k].getAttribute('data-veto') === 'true') {
                                var h = document.createElement('input');
                                h.type = 'hidden';
                                h.name = 'confirmedVetoes';
                                h.value = checked[k].value;
                                form.appendChild(h);
                            }
                        }
                    }
                    var msg = 'Apply ' + checked.length + ' pick' + (checked.length === 1 ? '' : 's') +
                              ' for Week ${week}?\n\nThis REPLACES any existing Week ${week} picks.';
                    if (!confirm(msg)) { ev.preventDefault(); }
                });
            })();
            </script>
        </c:otherwise>
    </c:choose>

</div>

<%-- <jsp:include page="/WEB-INF/views/footer.jsp" /> --%>
</body>
</html>
