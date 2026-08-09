-- Normalize team identifiers to apiTeamShortName abbreviations ("SEA", "BUF", ...).
--
-- Background: SqlConnectorGameTable.updateGameTableMinimal used a last-word substring
-- to "abbreviate" ESPN team names, which wrote mascot names ("Seahawks") into
-- Game.homeTeamName/awayTeamName. getGamesForWeek's Teams-join alias collided with
-- g.* so those mascot names reached Make Picks and leaked into Picks.selectedTeam,
-- splitting the same team across two strings ("BUF" vs "Bills").
--
-- Re-runnable: every statement is a no-op once the data is normalized.

UPDATE KOTH.Game g JOIN KOTH.Teams t ON g.homeTeamId = t.apiTeamID
SET g.homeTeamName = t.apiTeamShortName
WHERE g.homeTeamName <> t.apiTeamShortName;

UPDATE KOTH.Game g JOIN KOTH.Teams t ON g.awayTeamId = t.apiTeamID
SET g.awayTeamName = t.apiTeamShortName
WHERE g.awayTeamName <> t.apiTeamShortName;

-- Picks stored as mascot names ("Bears") or full names ("Chicago Bears") -> "CHI"
UPDATE KOTH.Picks p JOIN KOTH.Teams t ON p.selectedTeam = t.apiTeamName
SET p.selectedTeam = t.apiTeamShortName;

UPDATE KOTH.Picks p JOIN KOTH.Teams t ON p.selectedTeam = t.apiTeamFullName
SET p.selectedTeam = t.apiTeamShortName;
