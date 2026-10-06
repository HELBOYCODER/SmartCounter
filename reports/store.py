"""SQLite + CSV report storage."""
import os
import csv
import sqlite3
from datetime import datetime


class ReportStore:
    def __init__(self, db_path):
        self.db_path = db_path
        self._init_db()

    def _init_db(self):
        con = sqlite3.connect(self.db_path)
        con.execute(
            "CREATE TABLE IF NOT EXISTS sessions("
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            "count INTEGER, count_up INTEGER, count_down INTEGER,"
            "ts TEXT, duration REAL)"
        )
        con.close()

    def save(self, count, count_up, count_down, duration=0):
        con = sqlite3.connect(self.db_path)
        con.execute(
            "INSERT INTO sessions(count, count_up, count_down, ts, duration) "
            "VALUES (?,?,?,?,?)",
            (count, count_up, count_down,
             datetime.now().strftime("%Y-%m-%d %H:%M:%S"), duration),
        )
        con.commit()
        con.close()

    def history(self, limit=50):
        con = sqlite3.connect(self.db_path)
        rows = con.execute(
            "SELECT * FROM sessions ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
        con.close()
        return rows

    def export_csv(self, path):
        rows = self.history()
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["id", "count", "count_up", "count_down", "ts", "duration"])
            w.writerows(rows)
        return path

    @staticmethod
    def export_sessions_csv(sessions, path):
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["id", "count", "count_up", "count_down", "ts", "duration"])
            w.writerows(sessions)
        return path