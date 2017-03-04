package com.sfl.coolmonkey.coolfs.service.storage;

import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.sfl.coolmonkey.coolfs.persistence.repositories.storage.StorageRepository;
import com.sfl.coolmonkey.coolfs.service.common.exception.ServicesRuntimeException;
import com.sfl.coolmonkey.coolfs.service.storage.dto.FileMetaDataDto;
import com.sfl.coolmonkey.coolfs.service.storage.dto.FileStoreDto;
import com.sfl.coolmonkey.coolfs.service.storage.model.FileOrigin;
import com.sfl.coolmonkey.coolfs.service.storage.model.FileStoreData;
import com.sfl.coolmonkey.coolfs.service.test.AbstractServiceIntegrationTest;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * User: Arthur Asatryan
 * Company: SFL LLC
 * Date: 2/17/16
 * Time: 7:14 PM
 */
@Component
public class StorageServiceIntegrationTest extends AbstractServiceIntegrationTest {

    //region Constants
    private static final String TESTABLE_STREAM_STRING = "some test data for my input stream" + UUID.randomUUID().toString();
    //endregion

    //region Dependencies
    @Autowired
    private StorageService storageService;

    @Autowired
    private StorageRepository storageRepository;
    //endregion

    //region Constructors
    public StorageServiceIntegrationTest() {
    }
    //endregion

    //region Test methods
    @Test
    public void testCreate() {
        // prepare data
        final FileStoreDto dto = getHelper().createFileStoreDto(TESTABLE_STREAM_STRING);
        // run test scenario
        final String resultUuid = storageService.create(dto);
        assertNotNull(resultUuid);
        // in this case the dto's uuid is not null
        assertEquals(dto.getFileMetaDataDto().getUuid(), resultUuid);
        final FileStoreData fileStoreData = storageService.getByMetaUuid(resultUuid);
        assertFileStoreDto(dto, fileStoreData);
        assertEquals(TESTABLE_STREAM_STRING.length(), fileStoreData.getLength());
        // check file metadata
        final GridFSDBFile gridFSDBFile = storageRepository.findByMetaUuid(fileStoreData.getUuid());
        final DBObject metaData = gridFSDBFile.getMetaData();
        assertNotNull(metaData.get("uuid"));
        assertEquals(dto.getFileMetaDataDto().getCompanyUuid(), metaData.get("companyUuid"));
        assertEquals(dto.getFileMetaDataDto().getFileOrigin().toString(), metaData.get("fileOrigin"));
    }

    @Test
    public void testGetByUuid() {
        // prepare data
        final FileStoreData fileStoreData = getHelper().createAndPersistFileStoreData();
        // run test scenario
        final FileStoreData result = storageService.getByMetaUuid(fileStoreData.getUuid());
        assertNotNull(result);
        assertEquals(fileStoreData, result);
    }

    @Test
    public void testGetById() {
        // prepare data
        final FileStoreData fileStoreData = getHelper().createAndPersistFileStoreData();
        final GridFSDBFile gridFSDBFile = storageRepository.findByMetaUuid(fileStoreData.getUuid());
        // run test scenario
        final FileStoreData result = storageService.getById((ObjectId) gridFSDBFile.getId());
        assertNotNull(result);
        assertEquals(fileStoreData, result);
    }

    @Test
    public void testGetByMetaUuids() {
        // prepare test data
        final List<FileStoreData> fileStoreDataList = Arrays.asList(
                getHelper().createAndPersistFileStoreData(),
                getHelper().createAndPersistFileStoreData()
        );
        final List<String> uuids = fileStoreDataList.stream().map(FileStoreData::getUuid).collect(Collectors.toList());
        // run test scenario
        final List<FileStoreData> result = storageService.getByMetaUuids(uuids);
        assertNotNull(result);
        assertEquals(fileStoreDataList, result);
    }

    @Test
    public void testGetByCompanyUuidAndFileNameAndCreatedAfterAndOrigin() {
        // prepare test data
        final String companyUuid = UUID.randomUUID().toString();
        final String fileName = "fileName.txt";
        final DateTime now = DateTime.now();
        final FileOrigin fileOrigin = FileOrigin.IMPORT_CSV;
        final InputStream inputStream = IOUtils.toInputStream("file content");
        // Expected files
        final FileStoreData expectedFileStoreData = getHelper().createAndPersistFileStoreData(new FileStoreDto(inputStream, fileName, "", new FileMetaDataDto(UUID.randomUUID().toString(), companyUuid, fileOrigin)));
        // Unexpected files
        final FileStoreData unexpectedFileStoreDataBecauseNonMatchingCompanyUuid = getHelper().createAndPersistFileStoreData(new FileStoreDto(inputStream, fileName, "", new FileMetaDataDto(UUID.randomUUID().toString(), UUID.randomUUID().toString(), fileOrigin)));
        final FileStoreData unexpectedFileStoreDataBecauseNonMatchingFileName = getHelper().createAndPersistFileStoreData(new FileStoreDto(inputStream, "otherFileName", "", new FileMetaDataDto(UUID.randomUUID().toString(), companyUuid, fileOrigin)));
        final FileStoreData unexpectedFileStoreDataBecauseNonMatchingFileOrigin = getHelper().createAndPersistFileStoreData(new FileStoreDto(inputStream, fileName, "", new FileMetaDataDto(UUID.randomUUID().toString(), companyUuid, FileOrigin.ORDER_PDF)));
        final FileStoreData unexpectedFileStoreDataBecauseCreatedBefore = getHelper().createAndPersistFileStoreData(new FileStoreDto(inputStream, fileName, "", new FileMetaDataDto(UUID.randomUUID().toString(), companyUuid, fileOrigin)));
        final GridFSDBFile createdBeforeGridFSDBFile = storageRepository.findByMetaUuid(unexpectedFileStoreDataBecauseCreatedBefore.getUuid());
        createdBeforeGridFSDBFile.put("uploadDate", now.minusMinutes(2).toDate());
        createdBeforeGridFSDBFile.save();
        // run test scenario
        final List<FileStoreData> result = storageService.getByCompanyUuidAndFileNameAndCreatedAfterAndOrigin(companyUuid, fileName, now.minusMinutes(1).toDate(), fileOrigin);
        assertEquals(Collections.singletonList(expectedFileStoreData), result);
    }

    @Test
    public void testDeleteByMetaUuid() {
        // given
        final FileStoreData fileStoreData = getHelper().createAndPersistFileStoreData();
        // when
        storageService.deleteByMetaUuid(fileStoreData.getUuid());
        // then
        try {
            storageService.getByMetaUuid(fileStoreData.getUuid());
            fail("Exception should be thrown");
        } catch (final ServicesRuntimeException ex) {
            // Expected
        }
    }
    //endregion

    //region Utility methods
    private void assertFileStoreDto(final FileStoreDto dto, final FileStoreData result) {
        assertNotNull(dto);
        assertNotNull(result);
        assertEquals(dto.getFileName(), result.getFileName());
        assertEquals(dto.getContentType(), result.getContentType());
        try {
            assertEquals(TESTABLE_STREAM_STRING, IOUtils.toString(result.getInputStream()));
        } catch (IOException e) {
            fail("Exception should not be thrown");
        } finally {
            IOUtils.closeQuietly(dto.getInputStream());
            IOUtils.closeQuietly(result.getInputStream());
        }
    }
    //endregion

}
