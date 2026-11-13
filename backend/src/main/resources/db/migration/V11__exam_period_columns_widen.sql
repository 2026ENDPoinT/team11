-- V9에서 SMALLINT로 만들었던 세 컬럼이 엔티티(int/Integer, Hibernate 기본 매핑은 INTEGER)와
-- 안 맞아서 운영 배포 시 ddl-auto=validate가 실패했다(Schema validation: wrong column type).
-- V9는 이미 운영 DB에 적용돼 체크섬이 고정이라 직접 못 고치고 넓히는 마이그레이션을 추가한다.

ALTER TABLE exam_periods ALTER COLUMN daily_study_hours_max TYPE INTEGER;
ALTER TABLE exam_period_exams ALTER COLUMN difficulty TYPE INTEGER;
ALTER TABLE exam_period_exams ALTER COLUMN study_hours_needed TYPE INTEGER;
