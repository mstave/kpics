package kpics

const val query = """
    SELECT afile.ID_local,
		  afile.ID_global,
		  afile.baseName,
		  afile.extension,
		  afile.folder,
		  aroot.absolutePath,
		  afolder.pathFromRoot
		FROM main.kpics.AgLibraryFile afile
		  INNER JOIN main.kpics.AgLibraryFolder afolder
			ON  afile.folder = afolder.ID_local
		  INNER JOIN main.kpics.AgLibraryRootFolder aroot
			ON  afolder.rootFolder = aroot.ID_local
     """

/*

fun main(args: Array<String>) {
    val dropbox = "/Users/mstave/dropbox"
    val filename = java.io.File("$dropbox/pic.db").absolutePath
    val ds = SQLiteDataSource()
    ds.url = "jdbc:sqlite:$filename"
    Database.connect(ds)

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    transaction {
        //        logger.addLogger(StdOutSqlLogger)
        logger.addLogger(Slf4jSqlLogger)
        for (pic in LibFile.all()) {
//            for (pic in kpics.AgLibraryFile.selectAll()) {
            if (pic.modDateTime != pic.externalModDateTime) {
                println("They are different")
                println(pic)
            }
        }
//        println("Extensions: ${kpics.LibFile.find { kpics.AgLibraryFile.extension eq "MP4"}.joinToString { it.baseName }}")
        val result = LibFolder.wrapRows(
                AgLibraryFile.innerJoin(AgLibraryFolder).select { AgLibraryFile.extension eq "MP4" }
                                       ).toList()
//        println(result)
//        println(kpics.AgLibraryFile.slice(kpics.AgLibraryFile.baseName, kpics.AgLibraryFile.extension).select {
//            kpics.AgLibraryFile.extension eq "MP4"}.orderBy(kpics.AgLibraryFile.baseName).toList())
//        println("${kpics.LibFile.all().limit(10).joinToString { it.baseName + "\n"}}")
//        println("${kpics.LibFile.find { kpics.AgLibraryFile.extension eq "MP4"}.count()}")
        println("${LibFile.all().limit(3).toList()}")
    }
}
//    id_local
//    id_global
//    baseName
//    errorMessage
//    errorTime
//    extension
//    externalModTime
//    folder
//    idx_filename
//    importHash
//    lc_idx_filename
//    lc_idx_filenameExtension
//    md5
//    modTime
//    originalFilename
//    sidecarExtensions
//    index_AgLibraryFile_folder
//    index_AgLibraryFile_importHash
//    index_AgLibraryFile_nameAndFolder
//    sqlite_autoindex_AgLibraryFile_1
//    trigger_AgDNGProxyInfo_fileDeleted
//    trigger_AgDNGProxyInfo_fileInserted

        */