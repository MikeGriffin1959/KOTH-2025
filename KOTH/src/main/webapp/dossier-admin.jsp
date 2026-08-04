<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KOTH</title>
    <link rel="icon" type="image/png" href="KOTH-Tab-Icon.png">
    <link rel="manifest" href="manifest.webmanifest">
    <meta name="theme-color" content="#1A43BF">
    <link rel="apple-touch-icon" href="icon-180.png">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black">
    <script>if ('serviceWorker' in navigator) { window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js'); }); }</script>
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="styles.css">
    <style>
        .dossier-wrap { max-width: 860px; margin: 0 auto; padding: 10px 12px 40px; }
        .d-card { background-color: #111; border: 1px solid #444; margin-bottom: 18px; }
        .d-card .card-header { background-color: #1A43BF; color: #fff; font-weight: 600; }
        .d-card .card-body { color: #ddd; }
        .d-card label { font-size: .85rem; color: #bbb; margin-bottom: 2px; margin-top: 8px; }
        .d-card textarea, .d-card input { background-color: #1c1c22; color: #eee; border: 1px solid #555; }
        .d-card textarea:focus, .d-card input:focus { background-color: #1c1c22; color: #fff; }
        .d-hint { font-size: .75rem; color: #888; }
        .d-alert { display: none; margin-top: 8px; }
    </style>
</head>
<body>
<div class="container">
    <jsp:include page="header.jsp">
        <jsp:param name="pageTitle" value="Dossiers" />
    </jsp:include>
</div>

<div class="dossier-wrap">
    <h4 style="color:#fff;"><i class="fas fa-address-book"></i> Commentary Dossiers &mdash; ${season}</h4>
    <p class="d-hint">What the AI commentator knows about the pool and each player. Everything here flows
       into the commentary prompts. <b>Sensitivities always win</b> &mdash; they soften the tone for that
       player regardless of the snark level (especially eliminations).</p>

    <!-- Pool dossier -->
    <div class="card d-card">
        <div class="card-header"><i class="fas fa-users"></i> Pool Dossier (identity &amp; lore)</div>
        <div class="card-body">
            <form onsubmit="return savePool(this);">
                <label>Pool identity <span class="d-hint">(who is this crew?)</span></label>
                <textarea class="form-control" name="poolIdentity" rows="2">${poolDossier.poolIdentity}</textarea>
                <label>Pool history</label>
                <textarea class="form-control" name="poolHistory" rows="2">${poolDossier.poolHistory}</textarea>
                <label>Pool lore <span class="d-hint">(running jokes, traditions, legendary moments)</span></label>
                <textarea class="form-control" name="poolLore" rows="2">${poolDossier.poolLore}</textarea>
                <label>Commissioner notes</label>
                <textarea class="form-control" name="commissionerNotes" rows="2">${poolDossier.commissionerNotes}</textarea>
                <label>Tone guidance <span class="d-hint">(extra instructions for the commentator's voice)</span></label>
                <textarea class="form-control" name="toneGuidance" rows="2">${poolDossier.toneGuidance}</textarea>
                <button type="submit" class="btn btn-primary btn-sm mt-3">Save Pool Dossier</button>
                <div class="alert d-alert"></div>
            </form>
        </div>
    </div>

    <!-- Per-user dossiers -->
    <c:forEach var="d" items="${userDossiers}">
        <div class="card d-card">
            <div class="card-header"><i class="fas fa-user"></i> ${d.firstName} <span style="opacity:.7;">(${d.username})</span></div>
            <div class="card-body">
                <form onsubmit="return saveUser(this, ${d.userId});">
                    <label>Display name <span class="d-hint">(what commentary calls them; blank = ${d.firstName})</span></label>
                    <input type="text" class="form-control" name="displayName" maxlength="100" value="${d.displayName}">
                    <label>Personality <span class="d-hint">(pick style, quirks, reputation)</span></label>
                    <textarea class="form-control" name="personality" rows="2">${d.personality}</textarea>
                    <label>Rivalries</label>
                    <textarea class="form-control" name="rivalries" rows="2">${d.rivalries}</textarea>
                    <label>Sensitivities <span class="d-hint">(pull punches here &mdash; overrides snark, softens eliminations)</span></label>
                    <textarea class="form-control" name="sensitivities" rows="2">${d.sensitivities}</textarea>
                    <button type="submit" class="btn btn-primary btn-sm mt-3">Save</button>
                    <div class="alert d-alert"></div>
                </form>
            </div>
        </div>
    </c:forEach>
</div>

<script>
function postDossier(params, form) {
    var alertDiv = form.querySelector('.d-alert');
    return fetch('DossierServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
        body: params
    })
    .then(function (r) { return r.json(); })
    .then(function (data) {
        alertDiv.textContent = data.message;
        alertDiv.className = 'alert d-alert ' + (data.success ? 'alert-success' : 'alert-danger');
        alertDiv.style.display = 'block';
        setTimeout(function () { alertDiv.style.display = 'none'; }, 3500);
    })
    .catch(function (e) {
        alertDiv.textContent = 'Error: ' + e.message;
        alertDiv.className = 'alert d-alert alert-danger';
        alertDiv.style.display = 'block';
    });
}
function formParams(form) {
    var parts = [];
    form.querySelectorAll('input[name], textarea[name]').forEach(function (el) {
        parts.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));
    });
    return parts.join('&');
}
function savePool(form) {
    postDossier('action=savePoolDossier&' + formParams(form), form);
    return false;
}
function saveUser(form, userId) {
    postDossier('action=saveUserDossier&userId=' + userId + '&' + formParams(form), form);
    return false;
}
</script>
</body>
</html>
