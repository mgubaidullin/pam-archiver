package one.entropy.archiver;

import javax.annotation.Resource;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.sql.DataSource;

public class Datasources {

    @Resource(lookup = "java:/MainDS")
    DataSource mainDataSource;

    @Produces
    @Named("mainDS")
    public DataSource getMainDataSource() {
        return mainDataSource;
    }

    @Resource(lookup = "java:/ArchiveDS")
    DataSource archiveDataSource;

    @Produces
    @Named("archiveDS")
    public DataSource getArchiveDataSource() {
        return archiveDataSource;
    }

}
