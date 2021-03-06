package com.sfl.coolmonkey.coolfs.api.rest.resources.storage;

import com.sfl.coolmonkey.commons.api.model.response.ResultResponseModel;
import com.sfl.coolmonkey.coolfs.api.model.storage.FileLoadModel;
import com.sfl.coolmonkey.coolfs.api.model.storage.FileOriginModel;
import com.sfl.coolmonkey.coolfs.api.model.storage.FileUploadModel;
import com.sfl.coolmonkey.coolfs.api.model.storage.request.*;
import com.sfl.coolmonkey.coolfs.api.model.storage.response.LoadFileByUuidResponse;
import com.sfl.coolmonkey.coolfs.facade.storage.StorageFacade;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

/**
 * User: Arthur Asatryan
 * Company: SFL LLC
 * Date: 2/17/16
 * Time: 5:01 PM
 */
@Component
@Path("storage")
@Produces("application/json")
public class StorageResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageResource.class);

    //region Dependencies
    @Autowired
    private StorageFacade storageFacade;
    //endregion

    //region Constructors
    public StorageResource() {
        LOGGER.debug("Initializing storage resource");
    }
    //endregion

    //region Public methods
    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(@FormDataParam("file") final InputStream inputStream,
                           @FormDataParam("file") final FormDataContentDisposition fileDetail,
                           @FormDataParam("file") FormDataBodyPart body) {
        final String fileName = body.getHeaders().getFirst("FileOrigin-Name");
        final String contentType = body.getHeaders().getFirst("FileOrigin-MediaType");
        final String companyUuid = body.getHeaders().getFirst("FileOrigin-CompanyUuid");
        final String fileOriginString = body.getHeaders().getFirst("FileOrigin-FileOrigin");
        final String uploadFileMaxSize = body.getHeaders().getFirst("UploadFile-MaxSize");
        final FileOriginModel fileOriginModel = fileOriginString != null && !fileOriginString.isEmpty() ? FileOriginModel.valueOf(fileOriginString) : null;
        Assert.notNull(fileName, "The file name should not be null");
        final FileUploadModel fileUploadModel = new FileUploadModel(inputStream, decodeUrlEncodedString(fileName), contentType, fileOriginModel);
        final Long uploadFileMaxSizeLong = uploadFileMaxSize != null && !uploadFileMaxSize.isEmpty() ? Long.valueOf(uploadFileMaxSize) : null;
        final UploadFileRequest uploadFileRequest = new UploadFileRequest(companyUuid, fileUploadModel);
        uploadFileRequest.setMaxFileLength(uploadFileMaxSizeLong);
        return Response.ok(storageFacade.upload(uploadFileRequest)).build();
    }

    @GET
    @Path("by-uuid")
    public Response getFileInfoByUuid(@BeanParam final GetFileInfoByUuidRequest request) {
        return Response.ok(storageFacade.getFileInfoByUuid(request)).build();
    }

    @POST
    @Path("by-uuids")
    public Response getFileInfoByUuids(final GetFileInfoByUuidListRequest request) {
        return Response.ok(storageFacade.getFileInfoByUuids(request)).build();
    }

    @GET
    @Path("download-by-uuid")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFileByUuid(@BeanParam final LoadFileByUuidRequest request) {
        final ResultResponseModel<LoadFileByUuidResponse> result = storageFacade.loadFileByUuid(request);
        final FileLoadModel loadFileModel = result.getResponse().getLoadFileModel();
        return Response.ok(loadFileModel.getInputStream())
                .header("Content-Disposition", "attachment; filename=" + loadFileModel.getFileName())
                .header("FileOrigin-Name", loadFileModel.getFileName())
                .header("FileOrigin-MediaType", loadFileModel.getContentType())
                .build();
    }

    @GET
    @Path("load-by-uuid")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response loadFileByUuid(@BeanParam final LoadFileByUuidRequest request) {
        final ResultResponseModel<LoadFileByUuidResponse> result = storageFacade.loadFileByUuid(request);
        final FileLoadModel loadFileModel = result.getResponse().getLoadFileModel();
        return Response.ok(loadFileModel.getInputStream(), loadFileModel.getContentType()).build();
    }

    @POST
    @Path("check-import-already-uploaded")
    public Response checkImportAlreadyUploaded(final CheckImportAlreadyUploadedRequest request) {
        return Response.ok(storageFacade.checkImportAlreadyUploaded(request)).build();
    }

    @GET
    @Path("/heartbeat")
    public Response getHeartBeat() {
        return Response.ok("OK").build();
    }
    ///endregion

    //region Utility methods
    @Nullable
    private String decodeUrlEncodedString(final String encodedString) {
        try {
            return new URLCodec().decode(encodedString);
        } catch (DecoderException ignore) {
            // Ignore
        }
        return null;
    }
    //endregion

}
