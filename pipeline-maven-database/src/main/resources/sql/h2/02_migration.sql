// support maven artifact types such as "_maven_plugin"
ALTER TABLE MAVEN_ARTIFACT ALTER COLUMN TYPE varchar(64);

UPDATE VERSION SET VERSION = 2;





