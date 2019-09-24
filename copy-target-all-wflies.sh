set -eux

for WILDFLY_DIR in $WILDFLY_HOME ~/Software/wfly_copies/*
  do printf "copying target jar to ${WILDFLY_DIR}\n"; cp target/jboss-ejb-client-*.Final-SNAPSHOT.jar ${WILDFLY_DIR}/modules/system/layers/base/org/jboss/ejb-client/main/jboss-ejb-client.jar
done

