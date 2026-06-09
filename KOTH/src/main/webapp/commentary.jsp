<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"   uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"  %>
<%@ taglib prefix="fn"  uri="jakarta.tags.functions" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        body { background-color: #000; }
        .commentary-wrap { max-width: 760px; margin: 0 auto; padding: 10px 12px 40px; }
        .commentary-title {
            color: #fff; font-weight: 700; letter-spacing: .3px;
            display: flex; align-items: center; gap: 8px; margin: 12px 0 4px;
        }
        .commentary-title i { color: #3d8ef7; }
        .commentary-sub { color: #888; font-size: .8rem; margin-bottom: 14px; }
        .c-entry {
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            border: 1px solid #333; border-left: 4px solid #0261c2;
            border-radius: 6px; margin-bottom: 12px; overflow: hidden;
        }
        .c-head {
            display: flex; justify-content: space-between; align-items: center;
            padding: 8px 12px; background-color: rgba(0,0,0,.25);
            border-bottom: 1px solid rgba(255,255,255,.06);
        }
        .c-head-left { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
        .c-badge {
            font-size: .6rem; font-weight: 700; padding: 1px 6px; border-radius: 3px;
            text-transform: uppercase; letter-spacing: .5px;
        }
        .c-badge-TEST    { background-color: #6c757d; color: #fff; }
        .c-badge-PREVIEW { background-color: #0261c2; color: #fff; }
        .c-badge-REVEAL  { background-color: #17a2b8; color: #fff; }
        .c-badge-EVENT   { background-color: #dc3545; color: #fff; }
        .c-badge-RECAP   { background-color: #28a745; color: #fff; }
        .c-week { font-size: .7rem; color: #aaa; }
        .c-snark { font-size: .6rem; color: #bbb; border: 1px solid #444; border-radius: 3px; padding: 1px 5px; }
        .c-time { font-size: .7rem; color: #777; }
        .c-body { padding: 10px 12px; color: #d6d6d6; font-size: .92rem; line-height: 1.55; white-space: pre-wrap; }
        .c-empty { color: #888; text-align: center; padding: 40px 10px; }
        .c-refnote { color: #555; font-size: .7rem; text-align: center; margin-top: 6px; }
    </style>
</head>
<body>
<c:if test="${not isPopout}">
    <div class="container">
        <jsp:include page="header.jsp">
            <jsp:param name="pageTitle" value="Commentary" />
        </jsp:include>
    </div>
</c:if>

<div class="commentary-wrap">
    <div class="commentary-title">
        <i class="fa-solid fa-tower-broadcast"></i> Commentary
    </div>
    <div class="commentary-sub">${season} season &middot; newest first</div>

    <div id="commentaryList">
        <c:choose>
            <c:when test="${timelineCount == 0}">
                <div class="c-empty">No commentary yet. Once the commissioner enables it and the season gets going, blurbs will appear here.</div>
            </c:when>
            <c:otherwise>
                <c:forEach var="c" items="${timeline}">
                    <div class="c-entry">
                        <div class="c-head">
                            <div class="c-head-left">
                                <span class="c-badge c-badge-${c.streamType}">
                                    <c:choose>
                                        <c:when test="${c.streamType == 'EVENT' and not empty c.eventType}">${c.eventType}</c:when>
                                        <c:when test="${c.streamType == 'TEST'}">Test</c:when>
                                        <c:when test="${c.streamType == 'PREVIEW'}">Preview</c:when>
                                        <c:when test="${c.streamType == 'REVEAL'}">Reveal</c:when>
                                        <c:when test="${c.streamType == 'EVENT'}">Event</c:when>
                                        <c:when test="${c.streamType == 'RECAP'}">Recap</c:when>
                                        <c:otherwise>${c.streamType}</c:otherwise>
                                    </c:choose>
                                </span>
                                <span class="c-week">Week ${c.week}</span>
                                <span class="c-snark">snark ${c.snarkLevel}</span>
                            </div>
                            <span class="c-time">
                                <fmt:formatDate value="${c.createdAt}" pattern="EEE h:mm a" />
                            </span>
                        </div>
                        <div class="c-body">${fn:escapeXml(c.body)}</div>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </div>
    <div class="c-refnote" id="refNote"></div>
</div>

<script>
// Auto-refresh the timeline every 60s (rebuild the list from JSON).
(function () {
    const list = document.getElementById('commentaryList');
    const note = document.getElementById('refNote');

    function badgeLabel(streamType, eventType) {
        if (streamType === 'EVENT' && eventType) return eventType;
        switch (streamType) {
            case 'TEST': return 'Test';
            case 'PREVIEW': return 'Preview';
            case 'REVEAL': return 'Reveal';
            case 'EVENT': return 'Event';
            case 'RECAP': return 'Recap';
            default: return streamType || '';
        }
    }
    function esc(s) {
        return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function render(entries) {
        if (!entries.length) {
            list.innerHTML = '<div class="c-empty">No commentary yet.</div>';
            return;
        }
        list.innerHTML = entries.map(function (e) {
            return '<div class="c-entry">'
                + '<div class="c-head"><div class="c-head-left">'
                + '<span class="c-badge c-badge-' + esc(e.streamType) + '">' + esc(badgeLabel(e.streamType, e.eventType)) + '</span>'
                + '<span class="c-week">Week ' + e.week + '</span>'
                + '<span class="c-snark">snark ' + e.snark + '</span>'
                + '</div><span class="c-time">' + esc(e.time) + '</span></div>'
                + '<div class="c-body">' + esc(e.body) + '</div>'
                + '</div>';
        }).join('');
    }
    function refresh() {
        fetch('CommentaryServlet', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
            body: 'action=refreshTimeline'
        })
        .then(function (r) { return r.json(); })
        .then(function (data) { if (data.success) { render(data.entries); note.textContent = 'Updated ' + new Date().toLocaleTimeString(); } })
        .catch(function () { /* keep the last render */ });
    }
    setInterval(refresh, 60000);
})();
</script>
</body>
</html>
