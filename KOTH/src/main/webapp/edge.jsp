<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" errorPage="error.jsp" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH Edge Advisor</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        /* ── Edge page: dark-mode match to KOTH palette ── */
        #edgePage { margin: 0 auto; padding: 0 20px 40px; max-width: 1200px; color: #fff; }

        #edgePage h1 {
            color: #fff;
            font-size: 1.6rem;
            margin: 0 0 4px;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.8);
        }
        #edgePage .sub {
            color: #cfd6e4;
            font-size: 0.9rem;
            margin-bottom: 12px;
            text-shadow: 1px 1px 2px rgba(0,0,0,0.8);
        }
        #edgePage .built {
            display:inline-block;
            margin-left:10px;
            padding: 2px 8px;
            background: rgba(0,0,0,0.6);
            border-radius: 4px;
            font-size: 0.8rem;
            color: #cfd6e4;
        }

        /* Top action bar — Apply, Refresh, Allocation, Triage all here */
        #edgePage .action-bar {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 10px;
            padding: 12px 14px;
            background: rgba(0,0,0,0.75);
            border: 1px solid #fff;
            border-radius: 6px;
            box-shadow: 0 0 10px rgba(255,255,255,0.3);
            margin: 12px 0 18px;
        }
        #edgePage .apply-btn {
            background: #2e7d32;
            color: #fff;
            border: 1px solid #fff;
            padding: 8px 18px;
            border-radius: 5px;
            font-size: 0.95rem;
            font-weight: 700;
            cursor: pointer;
        }
        #edgePage .apply-btn:hover { background: #1b5e20; }
        #edgePage .apply-btn:disabled { background: #555; cursor: not-allowed; }
        #edgePage .warn-text { color: #ffc107; font-size: 0.8rem; }
        #edgePage .divider { width: 1px; align-self: stretch; background: rgba(255,255,255,0.2); margin: 0 4px; }

        #edgePage .toggle {
            text-decoration: none;
            padding: 5px 12px;
            border: 1px solid #ccc;
            border-radius: 4px;
            color: #fff;
            background: rgba(0,0,0,0.4);
            font-size: 0.85rem;
            font-weight: 600;
        }
        #edgePage .toggle:hover { background: rgba(26,67,191,0.4); border-color: #1A43BF; color:#fff; text-decoration:none; }
        #edgePage .toggle.active { background: #1A43BF; border-color: #1A43BF; color: #fff; }
        #edgePage .toggle.refresh { background: rgba(0,0,0,0.4); }
        #edgePage .toggle.refresh i { margin-right: 4px; }

        /* Banners */
        #edgePage .banner {
            padding: 10px 14px;
            border-radius: 5px;
            margin-bottom: 14px;
            font-size: 0.9rem;
            font-weight: 600;
        }
        #edgePage .banner.ok  { background: rgba(46,125,50,0.85);  color: #fff; border: 1px solid #2e7d32; }
        #edgePage .banner.err { background: rgba(178,38,30,0.85);  color: #fff; border: 1px solid #b3261e; }

        /* Card around the table for the floating-panel look */
        #edgePage .edge-card {
            background: rgba(0,0,0,0.85);
            border: 1px solid #fff;
            border-radius: 6px;
            box-shadow: 0 0 10px rgba(255,255,255,0.3);
            overflow: hidden;
        }

        /* Table */
        #edgePage table.edge-table {
            width: 100%;
            margin: 0;
            border-collapse: collapse;
            font-size: 0.85rem;
            color: #fff;
        }
        #edgePage table.edge-table thead th {
            background: #1A43BF;
            color: #fff;
            font-weight: 700;
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            padding: 10px 8px;
            border: none;
            text-align: center;
            position: sticky;
            top: 0;
        }
        #edgePage table.edge-table tbody td {
            background: #000;
            color: #fff;
            padding: 8px;
            border-bottom: 1px solid rgba(255,255,255,0.08);
            text-align: center;
            vertical-align: middle;
        }
        #edgePage table.edge-table tbody tr.rec td {
            background: rgba(46,125,50,0.25);
        }
        #edgePage table.edge-table td.team {
            text-align: left;
            font-weight: 700;
            font-size: 0.95rem;
        }
        #edgePage table.edge-table tbody tr.rec td.team::after {
            content: " ★";
            color: #4caf50;
        }
        #edgePage table.edge-table td.opp { text-align: left; color: #b8c0d0; }
        #edgePage table.edge-table th.cb,
        #edgePage table.edge-table td.cb { width: 32px; padding: 6px 0; }

        /* Probabilities & numbers */
        #edgePage .pct { font-variant-numeric: tabular-nums; }
        #edgePage .safety { font-weight: 700; color: #fff; }
        #edgePage .muted { color: #6e7a8a; }

        /* Flag chips */
        #edgePage .flag {
            display: inline-block;
            background: rgba(178,38,30,0.25);
            color: #ff8a80;
            border: 1px solid rgba(178,38,30,0.5);
            border-radius: 3px;
            padding: 1px 6px;
            margin: 1px;
            font-size: 0.7rem;
            font-weight: 600;
        }
        #edgePage .flag.div  {
            background: rgba(255,193,7,0.2);
            color: #ffc107;
            border-color: rgba(255,193,7,0.5);
        }
        #edgePage .flag.thin {
            background: rgba(255,255,255,0.08);
            color: #cfd6e4;
            border-color: rgba(255,255,255,0.2);
        }

        /* Claude column */
        #edgePage .crisk {
            display: inline-block;
            border-radius: 3px;
            padding: 2px 7px;
            font-size: 0.75rem;
            font-weight: 700;
        }
        #edgePage .crisk.low  { background: rgba(46,125,50,0.3);  color: #81c784; border: 1px solid #2e7d32; }
        #edgePage .crisk.med  { background: rgba(255,193,7,0.25); color: #ffd54f; border: 1px solid #ffc107; }
        #edgePage .crisk.high { background: rgba(178,38,30,0.3);  color: #ff8a80; border: 1px solid #b3261e; }
        #edgePage .veto {
            display: inline-block;
            background: #b3261e;
            color: #fff;
            border-radius: 3px;
            padding: 2px 7px;
            font-size: 0.7rem;
            font-weight: 800;
            margin-left: 6px;
            letter-spacing: 0.5px;
        }
        #edgePage .rationale { text-align: left; color: #cfd6e4; font-size: 0.8rem; line-height: 1.4; }
        #edgePage .lives { font-weight: 800; color: #81c784; font-size: 1rem; }

        /* Empty-state */
        #edgePage .empty {
            padding: 24px;
            text-align: center;
            color: #cfd6e4;
        }

        /* Checkboxes scaled up for easier clicking */
        #edgePage input[type="checkbox"] { transform: scale(1.3); cursor: pointer; }

        /* Note at bottom */
        #edgePage .note {
            margin-top: 14px;
            padding: 10px 14px;
            background: rgba(0,0,0,0.6);
            border-left: 3px solid #1A43BF;
            border-radius: 4px;
            font-size: 0.8rem;
            color: #cfd6e4;
            line-height: 1.5;
        }
        #edgePage .note strong { color: #fff; }

        /* Mobile */
        @media (max-width: 767px) {
            #edgePage { padding: 0 10px 30px; }
            #edgePage .action-bar { gap: 6px; padding: 10px; }
            #edgePage table.edge-table { font-size: 0.75rem; }
            #edgePage table.edge-table tbody td { padding: 6px 4px; }
            #edgePage .toggle { padding: 4px 8px; font-size: 0.75rem; }
        }
    </style>
</head>
<body>

<%-- Site header — utility bar + nav (matches every other KOTH page) --%>
<jsp:include page="header.jsp">
    <jsp:param name="pageTitle" value="Edge" />
</jsp:include>

<div id="edgePage">

    <h1><i class="fas fa-brain" style="color:#1A43BF; margin-right:8px;"></i>Edge Advisor</h1>
    <div class="sub">
        Season ${season} &middot; Week ${week} &middot; allocating <strong>${lives}</strong>
        life<c:if test="${lives != 1}">s</c:if> &middot; mode: <strong>${alloc}</strong>
        <c:if test="${lastBuiltAt != null}">
            <span class="built">
                <i class="far fa-clock"></i>
                Last built <fmt:formatDate value="${lastBuiltAt}" pattern="MMM d, h:mm a"/>
            </span>
        </c:if>
    </div>

    <c:if test="${not empty applyMessage}">
        <div class="banner ok"><i class="fas fa-check-circle"></i> <c:out value="${applyMessage}"/></div>
    </c:if>
    <c:if test="${not empty applyError}">
        <div class="banner err"><i class="fas fa-exclamation-triangle"></i> <c:out value="${applyError}"/></div>
    </c:if>
    <c:if test="${not empty buildError}">
        <div class="banner err"><i class="fas fa-exclamation-triangle"></i> <c:out value="${buildError}"/></div>
    </c:if>

    <c:choose>
        <c:when test="${empty candidates}">
            <div class="edge-card">
                <div class="empty">
                    No candidates for this week. Hit
                    <a href="?season=${season}&amp;week=${week}&amp;refresh=true" class="toggle refresh">
                        <i class="fas fa-sync-alt"></i> Build snapshots
                    </a>
                    to fetch lines and rank.
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <form id="edgeApplyForm" method="post" action="${pageContext.request.contextPath}/admin/edge/apply">
                <input type="hidden" name="season" value="${season}"/>
                <input type="hidden" name="week" value="${week}"/>

                <%-- ═════════════════════════ TOP ACTION BAR ═════════════════════════ --%>
                <div class="action-bar">
                    <button type="submit" class="apply-btn" id="applyBtn">
                        <i class="fas fa-check"></i> Apply Selected Picks
                    </button>
                    <span class="warn-text">
                        <i class="fas fa-exclamation-triangle"></i>
                        Applying replaces <strong>all</strong> Week ${week} picks
                    </span>

                    <div class="divider"></div>

                    <a href="?season=${season}&amp;week=${week}&amp;lives=${lives}&amp;alloc=${alloc}&amp;triage=${triage}&amp;refresh=true"
                       class="toggle refresh" title="Rebuild snapshots from latest odds + FPI">
                        <i class="fas fa-sync-alt"></i> Refresh
                    </a>

                    <div class="divider"></div>

                    <span style="font-size:0.8rem; color:#cfd6e4;">Allocation:</span>
                    <a href="?season=${season}&amp;week=${week}&amp;lives=${lives}&amp;alloc=SPREAD&amp;triage=${triage}"
                       class="toggle ${alloc == 'SPREAD' ? 'active' : ''}">Spread</a>
                    <a href="?season=${season}&amp;week=${week}&amp;lives=${lives}&amp;alloc=STACK&amp;triage=${triage}"
                       class="toggle ${alloc == 'STACK' ? 'active' : ''}">Stack</a>

                    <div class="divider"></div>

                    <a href="?season=${season}&amp;week=${week}&amp;lives=${lives}&amp;alloc=${alloc}&amp;triage=true"
                       class="toggle ${triage ? 'active' : ''}" title="Run Claude Haiku triage on top candidates">
                        <i class="fas fa-brain"></i> ${triage ? 'Claude On' : 'Run Claude Triage'}
                    </a>
                </div>

                <%-- ═════════════════════════ TABLE ═════════════════════════ --%>
                <div class="edge-card">
                <table class="edge-table">
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
                                <td class="rationale"><c:out value="${c.claudeRationale}"/></td>
                            </c:if>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
                </div>
            </form>

            <div class="note">
                <strong>How to read this:</strong> Safety score = blended win probability minus penalties
                for model/market divergence and situational upset risk. The list is ranked by safety, not raw
                probability &mdash; so a clean favorite can outrank a higher-probability team that carries flags.
                Highlighted rows (★) are where your lives are allocated. Checkboxes are pre-selected to the
                allocation; uncheck or check rows to override before applying.
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

</body>
</html>
