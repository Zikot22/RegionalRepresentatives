from pathlib import Path
from contextlib import contextmanager
import sqlite3
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Regional Representatives API", version="1.0.0")
DB_PATH = Path(__file__).resolve().parent / "representatives.db"

@contextmanager
def db():
	connection = sqlite3.connect(DB_PATH)
	connection.row_factory = sqlite3.Row
	connection.execute("PRAGMA foreign_keys = ON")
	try:
		yield connection
		connection.commit()
	except Exception:
		connection.rollback()
		raise
	finally:
		connection.close()

def init_db():
	with db() as c:
		c.execute("""CREATE TABLE IF NOT EXISTS Region (
			Id INTEGER PRIMARY KEY AUTOINCREMENT,
			RegionDiscription TEXT NOT NULL)""")
		c.execute("""CREATE TABLE IF NOT EXISTS Territory (
			Id INTEGER PRIMARY KEY AUTOINCREMENT,
			RegionId INTEGER NOT NULL,
			Discripsion TEXT NOT NULL,
			FOREIGN KEY (RegionId) REFERENCES Region(Id)
			ON UPDATE CASCADE ON DELETE CASCADE)""")
		c.execute("""CREATE TABLE IF NOT EXISTS Employee (
			Id INTEGER PRIMARY KEY AUTOINCREMENT,
			LastName TEXT NOT NULL, FirstName TEXT NOT NULL,
			SecondName TEXT, Title TEXT, BirthDay TEXT, Address TEXT,
			City TEXT, Region TEXT, Phone TEXT, Email TEXT)""")
		c.execute("""CREATE TABLE IF NOT EXISTS EmployeeTerritory (
			EmployeeId INTEGER NOT NULL, TerritoryId INTEGER NOT NULL,
			PRIMARY KEY (EmployeeId, TerritoryId),
			FOREIGN KEY (EmployeeId) REFERENCES Employee(Id) ON DELETE CASCADE,
			FOREIGN KEY (TerritoryId) REFERENCES Territory(Id) ON DELETE CASCADE)""")

init_db()

class RegionInput(BaseModel):
	RegionDiscription: str = Field(min_length=1, max_length=150)

class TerritoryInput(BaseModel):
	RegionId: int = Field(gt=0)
	Discripsion: str = Field(min_length=1, max_length=150)

class EmployeeInput(BaseModel):
	LastName: str = Field(min_length=1, max_length=100)
	FirstName: str = Field(min_length=1, max_length=100)
	SecondName: Optional[str] = None
	Title: Optional[str] = None
	BirthDay: Optional[str] = None # YYYY-MM-DD
	Address: Optional[str] = None
	City: Optional[str] = None
	Region: Optional[str] = None
	Phone: Optional[str] = None
	Email: Optional[str] = None

class AssignmentInput(BaseModel):
	EmployeeId: int = Field(gt=0)
	TerritoryId: int = Field(gt=0)

def not_found(row, name):
	if row is None:
		raise HTTPException(status_code=404, detail=f"{name} не найден(а)")
	return dict(row)

def integrity_error(error):
	if "FOREIGN KEY constraint failed" in str(error):
		raise HTTPException(status_code=400, detail="Указанный связанный объект не существует.")
	if "UNIQUE constraint failed" in str(error):
		raise HTTPException(status_code=409, detail="Такая связь или запись уже существует.")
	raise HTTPException(status_code=400, detail="Ошибка целостности базы данных.")

@app.get("/")
def home():
	return {"message": "Regional Representatives API is running"}

@app.get("/health")
def health():
	return {"status": "ok"}

# REGIONS: list, get, create, update, delete
@app.get("/regions", tags=["Регионы"])
def list_regions():
	with db() as c:
		return [dict(r) for r in c.execute(
			"SELECT Id, RegionDiscription FROM Region ORDER BY Id").fetchall()]

@app.get("/regions/{region_id}", tags=["Регионы"])
def get_region(region_id: int):
	with db() as c:
		return not_found(c.execute(
			"SELECT Id, RegionDiscription FROM Region WHERE Id=?", (region_id,)
		).fetchone(), "Регион")

@app.post("/regions", status_code=201, tags=["Регионы"])
def create_region(data: RegionInput):
	with db() as c:
		cur = c.execute("INSERT INTO Region (RegionDiscription) VALUES (?)",
			(data.RegionDiscription.strip(),))
		return dict(c.execute("SELECT Id, RegionDiscription FROM Region WHERE Id=?",
			(cur.lastrowid,)).fetchone())

@app.put("/regions/{region_id}", tags=["Регионы"])
def update_region(region_id: int, data: RegionInput):
	with db() as c:
		cur = c.execute("UPDATE Region SET RegionDiscription=? WHERE Id=?",
			(data.RegionDiscription.strip(), region_id))
		if not cur.rowcount:
			raise HTTPException(404, "Регион не найден")
		return dict(c.execute("SELECT Id, RegionDiscription FROM Region WHERE Id=?",
			(region_id,)).fetchone())

@app.delete("/regions/{region_id}", tags=["Регионы"])
def delete_region(region_id: int):
	with db() as c:
		cur = c.execute("DELETE FROM Region WHERE Id=?", (region_id,))
		if not cur.rowcount:
			raise HTTPException(404, "Регион не найден")
		return {"message": "Регион удалён", "Id": region_id}

# TERRITORIES / CITIES: list, get, create, update, delete
@app.get("/territories", tags=["Города / территории"])
def list_territories(region_id: Optional[int] = None):
	sql = """SELECT t.Id, t.RegionId, t.Discripsion, r.RegionDiscription
		FROM Territory t JOIN Region r ON r.Id=t.RegionId"""
	args = ()
	if region_id is not None:
		sql += " WHERE t.RegionId=?"
		args = (region_id,)
	sql += " ORDER BY t.Id"
	with db() as c:
		return [dict(r) for r in c.execute(sql, args).fetchall()]

@app.get("/territories/{territory_id}", tags=["Города / территории"])
def get_territory(territory_id: int):
	with db() as c:
		row = c.execute("""SELECT t.Id, t.RegionId, t.Discripsion, r.RegionDiscription
			FROM Territory t JOIN Region r ON r.Id=t.RegionId WHERE t.Id=?""",
			(territory_id,)).fetchone()
		return not_found(row, "Город/территория")

@app.post("/territories", status_code=201, tags=["Города / территории"])
def create_territory(data: TerritoryInput):
	try:
		with db() as c:
			cur = c.execute("INSERT INTO Territory (RegionId, Discripsion) VALUES (?, ?)",
				(data.RegionId, data.Discripsion.strip()))
			row = c.execute("""SELECT t.Id, t.RegionId, t.Discripsion, r.RegionDiscription
				FROM Territory t JOIN Region r ON r.Id=t.RegionId WHERE t.Id=?""",
				(cur.lastrowid,)).fetchone()
			return dict(row)
	except sqlite3.IntegrityError as e:
		integrity_error(e)

@app.put("/territories/{territory_id}", tags=["Города / территории"])
def update_territory(territory_id: int, data: TerritoryInput):
	try:
		with db() as c:
			cur = c.execute("UPDATE Territory SET RegionId=?, Discripsion=? WHERE Id=?",
				(data.RegionId, data.Discripsion.strip(), territory_id))
			if not cur.rowcount:
				raise HTTPException(404, "Город/территория не найден(а)")
			row = c.execute("""SELECT t.Id, t.RegionId, t.Discripsion, r.RegionDiscription
				FROM Territory t JOIN Region r ON r.Id=t.RegionId WHERE t.Id=?""",
				(territory_id,)).fetchone()
			return dict(row)
	except sqlite3.IntegrityError as e:
		integrity_error(e)

@app.delete("/territories/{territory_id}", tags=["Города / территории"])
def delete_territory(territory_id: int):
	with db() as c:
		cur = c.execute("DELETE FROM Territory WHERE Id=?", (territory_id,))
		if not cur.rowcount:
			raise HTTPException(404, "Город/территория не найден(а)")
		return {"message": "Город/территория удалён(а)", "Id": territory_id}

# EMPLOYEES: list, get, create, update, delete
EMPLOYEE_FIELDS = ("LastName", "FirstName", "SecondName", "Title", "BirthDay",
	"Address", "City", "Region", "Phone", "Email")
EMPLOYEE_SELECT = ", ".join(EMPLOYEE_FIELDS)

@app.get("/employees", tags=["Сотрудники"])
def list_employees():
	with db() as c:
		return [dict(r) for r in c.execute(
			f"SELECT Id, {EMPLOYEE_SELECT} FROM Employee ORDER BY Id").fetchall()]

@app.get("/employees/{employee_id}", tags=["Сотрудники"])
def get_employee(employee_id: int):
	with db() as c:
		row = c.execute(f"SELECT Id, {EMPLOYEE_SELECT} FROM Employee WHERE Id=?",
			(employee_id,)).fetchone()
		return not_found(row, "Сотрудник")

@app.post("/employees", status_code=201, tags=["Сотрудники"])
def create_employee(data: EmployeeInput):
	values = data.dict()
	columns = list(values)
	sql = f"INSERT INTO Employee ({', '.join(columns)}) VALUES ({', '.join('?' for _ in columns)})"
	with db() as c:
		cur = c.execute(sql, tuple(values.values()))
		row = c.execute(f"SELECT Id, {EMPLOYEE_SELECT} FROM Employee WHERE Id=?",
			(cur.lastrowid,)).fetchone()
		return dict(row)

@app.put("/employees/{employee_id}", tags=["Сотрудники"])
def update_employee(employee_id: int, data: EmployeeInput):
	values = data.dict()
	sql = "UPDATE Employee SET " + ", ".join(f"{k}=?" for k in values) + " WHERE Id=?"
	with db() as c:
		cur = c.execute(sql, tuple(values.values()) + (employee_id,))
		if not cur.rowcount:
			raise HTTPException(404, "Сотрудник не найден")
		row = c.execute(f"SELECT Id, {EMPLOYEE_SELECT} FROM Employee WHERE Id=?",
			(employee_id,)).fetchone()
		return dict(row)

@app.delete("/employees/{employee_id}", tags=["Сотрудники"])
def delete_employee(employee_id: int):
	with db() as c:
		cur = c.execute("DELETE FROM Employee WHERE Id=?", (employee_id,))
		if not cur.rowcount:
			raise HTTPException(404, "Сотрудник не найден")
		return {"message": "Сотрудник удалён", "Id": employee_id}

# Employee-to-territory assignments
@app.get("/employee-territories", tags=["Назначение территорий"])
def list_assignments(employee_id: Optional[int] = None,
				     territory_id: Optional[int] = None):
	sql = """SELECT et.EmployeeId, et.TerritoryId, e.LastName, e.FirstName,
				e.SecondName, t.Discripsion AS TerritoryName, r.RegionDiscription
			FROM EmployeeTerritory et
			JOIN Employee e ON e.Id=et.EmployeeId
			JOIN Territory t ON t.Id=et.TerritoryId
			JOIN Region r ON r.Id=t.RegionId"""
	filters, args = [], []
	if employee_id is not None:
		filters.append("et.EmployeeId=?")
		args.append(employee_id)
	if territory_id is not None:
		filters.append("et.TerritoryId=?")
		args.append(territory_id)
	if filters:
		sql += " WHERE " + " AND ".join(filters)
	sql += " ORDER BY et.EmployeeId, et.TerritoryId"
	with db() as c:
		return [dict(r) for r in c.execute(sql, tuple(args)).fetchall()]

@app.post("/employee-territories", status_code=201, tags=["Назначение территорий"])
def assign_territory(data: AssignmentInput):
	try:
		with db() as c:
			c.execute("INSERT INTO EmployeeTerritory (EmployeeId, TerritoryId) VALUES (?, ?)",
				(data.EmployeeId, data.TerritoryId))
		return {"EmployeeId": data.EmployeeId, "TerritoryId": data.TerritoryId,
			"message": "Территория назначена сотруднику"}
	except sqlite3.IntegrityError as e:
		integrity_error(e)

@app.delete("/employee-territories/{employee_id}/{territory_id}",
	tags=["Назначение территорий"])
def unassign_territory(employee_id: int, territory_id: int):
	with db() as c:
		cur = c.execute("DELETE FROM EmployeeTerritory WHERE EmployeeId=? AND TerritoryId=?",
			(employee_id, territory_id))
		if not cur.rowcount:
			raise HTTPException(404, "Назначение не найдено")
		return {"message": "Территория отвязана от сотрудника"}