-- Move the previous default curfew to 22:00 while preserving any manager-customized time.
UPDATE system_config
SET checkout_curfew_time = '22:00:00'
WHERE id = 1 AND checkout_curfew_time = '20:00:00';
