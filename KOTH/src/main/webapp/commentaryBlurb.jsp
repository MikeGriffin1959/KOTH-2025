<%--
    commentaryBlurb.jsp — latest AI commentary teaser for the Home page.
    Include via: <jsp:include page="commentaryBlurb.jsp" />
    Required request attributes (set by HomeServlet):
      - latestCommentary : model.Commentary (nullable)
      - commentaryEnabled : Boolean
    Renders nothing when commentary is disabled or there is no entry yet.
--%>
<%@ taglib prefix="c"   uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt"  %>
<%@ taglib prefix="fn"  uri="jakarta.tags.functions" %>

<c:if test="${commentaryEnabled and latestCommentary != null}">
<div class="commentary-blurb">
    <div class="blurb-header">
        <div class="blurb-left">
            <i class="fa-solid fa-tower-broadcast blurb-icon"></i>
            <span class="blurb-label">Commentary</span>
            <span class="blurb-type-badge blurb-badge-${latestCommentary.streamType}">
                <c:choose>
                    <c:when test="${latestCommentary.streamType == 'EVENT' and not empty latestCommentary.eventType}">${latestCommentary.eventType}</c:when>
                    <c:when test="${latestCommentary.streamType == 'TEST'}">Test</c:when>
                    <c:when test="${latestCommentary.streamType == 'PREVIEW'}">Preview</c:when>
                    <c:when test="${latestCommentary.streamType == 'REVEAL'}">Reveal</c:when>
                    <c:when test="${latestCommentary.streamType == 'EVENT'}">Event</c:when>
                    <c:when test="${latestCommentary.streamType == 'RECAP'}">Recap</c:when>
                    <c:otherwise>${latestCommentary.streamType}</c:otherwise>
                </c:choose>
            </span>
        </div>
        <div class="blurb-right">
            <span class="blurb-time">
                <fmt:formatDate value="${latestCommentary.createdAt}" pattern="EEE h:mm a" />
            </span>
        </div>
    </div>
    <div class="blurb-body">
        ${fn:escapeXml(fn:substring(latestCommentary.body, 0, 280))}<c:if test="${fn:length(latestCommentary.body) > 280}">&hellip;</c:if>
    </div>
    <div class="blurb-footer">
        <a href="CommentaryServlet" class="blurb-link">
            Read full commentary <i class="fas fa-arrow-right"></i>
        </a>
        <button type="button" class="blurb-popout-btn d-none d-md-inline-block"
                onclick="openCommentaryPopout()" title="Open in pop-out window">
            <i class="fas fa-up-right-from-square"></i>
        </button>
    </div>
</div>

<style>
    .commentary-blurb {
        background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
        border: 1px solid #333; border-left: 4px solid #0261c2; border-radius: 6px;
        margin: 0 0 18px 15px; max-width: 560px; overflow: hidden; transition: border-color .2s;
    }
    .commentary-blurb:hover { border-left-color: #3d8ef7; }
    .blurb-header {
        display: flex; justify-content: space-between; align-items: center;
        padding: 8px 12px; background-color: rgba(0,0,0,.25); border-bottom: 1px solid rgba(255,255,255,.06);
    }
    .blurb-left { display: flex; align-items: center; gap: 6px; }
    .blurb-icon { color: #0261c2; font-size: .85rem; }
    .blurb-label { font-size: .78rem; font-weight: 600; color: #ccc; letter-spacing: .3px; }
    .blurb-type-badge { font-size: .6rem; font-weight: 700; padding: 1px 6px; border-radius: 3px; text-transform: uppercase; letter-spacing: .5px; }
    .blurb-badge-TEST    { background-color: #6c757d; color: #fff; }
    .blurb-badge-PREVIEW { background-color: #0261c2; color: #fff; }
    .blurb-badge-REVEAL  { background-color: #17a2b8; color: #fff; }
    .blurb-badge-EVENT   { background-color: #dc3545; color: #fff; }
    .blurb-badge-RECAP   { background-color: #28a745; color: #fff; }
    .blurb-time { font-size: .7rem; color: #777; }
    .blurb-body { padding: 10px 12px; color: #d0d0d0; font-size: .88rem; line-height: 1.55; }
    .blurb-footer {
        display: flex; justify-content: space-between; align-items: center;
        padding: 6px 12px; border-top: 1px solid rgba(255,255,255,.06);
    }
    .blurb-link { font-size: .78rem; color: #3d8ef7; text-decoration: none; font-weight: 500; }
    .blurb-link:hover { color: #6baaff; text-decoration: none; }
    .blurb-link i { font-size: .65rem; margin-left: 3px; }
    .blurb-popout-btn {
        background: none; border: 1px solid #555; color: #777; padding: 2px 8px;
        border-radius: 3px; font-size: .7rem; cursor: pointer; transition: all .2s;
    }
    .blurb-popout-btn:hover { border-color: #0261c2; color: #0261c2; }
    @media (max-width: 576px) {
        .commentary-blurb { margin-left: 5px; }
        .blurb-body { font-size: .82rem; padding: 8px 10px; }
    }
</style>

<script>
function openCommentaryPopout() {
    var width = 520;
    var height = Math.min(window.screen.availHeight - 60, 900);
    var left = window.screen.availWidth - width - 20;
    window.open('CommentaryServlet?popout=true', 'commentary_popout',
        'width=' + width + ',height=' + height + ',left=' + left + ',top=40,scrollbars=yes,resizable=yes');
}
</script>
</c:if>
