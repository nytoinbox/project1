# Visual Query Designer (Java Swing สำหรับ Oracle)

เครื่องมือออกแบบ query แบบลากวาง คล้าย Query Designer ของ Microsoft SQL Server (SSMS)

- ลากตารางจากรายชื่อด้านซ้ายมาวางบน diagram
  - ถ้ามี Foreign Key ระบบจะสร้าง JOIN ให้อัตโนมัติ
- ลากคอลัมน์ไปวางบนคอลัมน์ของอีกตาราง เพื่อสร้าง JOIN เอง
  - คลิกขวาที่เส้น join เพื่อเปลี่ยนเป็น INNER / LEFT / RIGHT / FULL หรือลบ
- ติ๊ก checkbox เพื่อเลือกคอลัมน์ แล้วกำหนดรายละเอียดใน grid ได้แก่ Alias, Output, Group By / Sum / Count / Count Distinct / Avg / Min / Max, Sort และ Filter
  - filter ของคอลัมน์ปกติจะไปอยู่ใน `WHERE`
  - filter ของคอลัมน์ aggregate จะไปอยู่ใน `HAVING`
- SQL (รูปแบบ Oracle 12c ขึ้นไป) จะถูกสร้างใหม่ทันทีที่มีการเปลี่ยนแปลง กด Copy ได้ และแก้ข้อความเองก่อนรันได้
- กด **Run (F5)** เพื่อดูข้อมูล
  - ตอนดูข้อมูลจะจำกัดจำนวนแถวด้วย `FETCH FIRST n ROWS ONLY`
  - รันได้เฉพาะ `SELECT` / `WITH` เท่านั้น

## ความต้องการ
- Java 21+
- Maven 3.9+

## Build และรัน
```bash
mvn package
java -jar target/visual-query-designer-0.1.0-SNAPSHOT.jar          # เปิดโปรแกรม
java -jar target/visual-query-designer-0.1.0-SNAPSHOT.jar --demo   # เปิดพร้อมฐานข้อมูลตัวอย่าง
```

## เชื่อมต่อ Oracle
กดปุ่ม **Connect to Oracle...** แล้วกรอกข้อมูลต่อไปนี้:
- Host, Port (ค่าเริ่มต้น 1521), Service name, Username, Password
- หรือติ๊กใช้ JDBC URL เต็ม เช่น `jdbc:oracle:thin:@//db-host:1521/ORCLPDB1`

Schema ที่จะแสดงรายชื่อตาราง ค่าเริ่มต้นคือ schema ของ user ที่ใช้เชื่อมต่อ และเปลี่ยนได้จาก dropdown ด้านซ้าย

## ฐานข้อมูลตัวอย่าง (Demo DB)
กดปุ่ม **Open demo DB (H2)** เพื่อเปิดฐานข้อมูล H2 ในหน่วยความจำ โหมด Oracle ซึ่งมีตาราง:
- `CUSTOMERS`
- `PRODUCTS`
- `ORDERS`
- `ORDER_ITEMS`

ใช้ลองเครื่องมือได้โดยไม่ต้องมี Oracle server

## โครงสร้างโค้ด
| แพ็กเกจ | หน้าที่ |
|---|---|
| `model` | โครงสร้าง query: ตาราง, join, คอลัมน์ใน grid (ไม่ผูกกับ UI) |
| `sql` | `OracleSqlGenerator` แปลง model เป็น SQL ของ Oracle |
| `db` | เชื่อมต่อ JDBC, อ่าน metadata (ตาราง/คอลัมน์/PK/FK), รัน query, demo DB |
| `ui` | หน้าจอ Swing: รายชื่อตาราง, diagram, criteria grid, SQL, ผลลัพธ์ |

## ทดสอบ
```bash
mvn test
```
