CREATE TABLE MAVEN_ARTIFACT
(
  GROUP_ID varchar(256) NOT NULL,
  ARTIFACT_ID varchar(256) NOT NULL,
  VERSION varchar(256) NOT NULL,
  TYPE varchar(10) NOT NULL,
  ID integer AUTO_INCREMENT PRIMARY KEY NOT NULL
);
CREATE INDEX IDX_MAVEN_ARTIFACT on MAVEN_ARTIFACT (GROUP_ID, ARTIFACT_ID, VERSION, TYPE);


CREATE TABLE JENKINS_JOB
(
  FULL_NAME varchar(512) NOT NULL,
  ID integer AUTO_INCREMENT PRIMARY KEY NOT NULL
);
CREATE UNIQUE INDEX IDX_JENKINS_JOB on JENKINS_JOB (FULL_NAME);

CREATE TABLE JENKINS_BUILD
(
  JOB_ID integer NOT NULL,
  NUMBER integer NOT NULL,
  ID integer AUTO_INCREMENT PRIMARY KEY NOT NULL,
  CONSTRAINT DEPENDENCY_JOB_ID_FK FOREIGN KEY (JOB_ID) REFERENCES JENKINS_JOB (ID) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IDX_JENKINS_BUILD on JENKINS_BUILD (JOB_ID, NUMBER);


CREATE TABLE MAVEN_DEPENDENCY
(
  ARTIFACT_ID integer NOT NULL,
  BUILD_ID integer NOT NULL,
  SCOPE varchar(20),
  ID integer AUTO_INCREMENT PRIMARY KEY NOT NULL,
  CONSTRAINT DEPENDENCY_BUILD_ID_FK FOREIGN KEY (BUILD_ID) REFERENCES JENKINS_BUILD (ID) ON DELETE CASCADE,
  CONSTRAINT DEPENDENCY_ARTIFACT_ID_FK FOREIGN KEY (ARTIFACT_ID) REFERENCES MAVEN_ARTIFACT(ID) ON DELETE CASCADE
);

CREATE TABLE GENERATED_MAVEN_ARTIFACT
(
  ARTIFACT_ID INT NOT NULL,
  BUILD_ID INT NOT NULL,
  ID INT AUTO_INCREMENT PRIMARY KEY NOT NULL,
  CONSTRAINT GENERATED_MAVEN_ARTIFACT_BUILD_ID_FK FOREIGN KEY (BUILD_ID) REFERENCES JENKINS_BUILD (ID) ON DELETE CASCADE,
  CONSTRAINT GENERATED_MAVEN_ARTIFACT_ARTIFACT_ID_FK FOREIGN KEY (ARTIFACT_ID) REFERENCES MAVEN_ARTIFACT (ID) ON DELETE CASCADE
);


CREATE TABLE VERSION
(
  VERSION integer
);

INSERT INTO VERSION(VERSION) VALUES (1);
