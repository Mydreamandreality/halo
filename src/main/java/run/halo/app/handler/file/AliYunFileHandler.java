package run.halo.app.handler.file;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import run.halo.app.exception.FileOperationException;
import run.halo.app.model.enums.AttachmentType;
import run.halo.app.model.properties.AliYunProperties;
import run.halo.app.model.support.UploadResult;
import run.halo.app.service.OptionService;
import run.halo.app.utils.FilenameUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * AliYun file handler.
 *
 * @author MyFaith
 * @author ryanwang
 * @date 2019-04-04
 */
@Slf4j
@Component
public class AliYunFileHandler implements FileHandler {

    private final OptionService optionService;

    public AliYunFileHandler(OptionService optionService) {
        this.optionService = optionService;
    }

    @Override
    public UploadResult upload(MultipartFile file) {
        Assert.notNull(file, "Multipart file must not be null");

        // Get config
        String protocol = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_PROTOCOL).toString();
        String domain = optionService.getByPropertyOrDefault(AliYunProperties.OSS_DOMAIN, String.class, "");
        String endPoint = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ENDPOINT).toString();
        String accessKey = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ACCESS_KEY).toString();
        String accessSecret = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ACCESS_SECRET).toString();
        String bucketName = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_BUCKET_NAME).toString();
        String source = StringUtils.join(protocol, bucketName, "." + endPoint);
        String styleRule = optionService.getByPropertyOrDefault(AliYunProperties.OSS_STYLE_RULE, String.class, "");
        String thumbnailStyleRule = optionService.getByPropertyOrDefault(AliYunProperties.OSS_THUMBNAIL_STYLE_RULE, String.class, "");

        // Init OSS client
        OSS ossClient = new OSSClientBuilder().build(endPoint, accessKey, accessSecret);

        domain = protocol + domain;

        try {
            String basename = FilenameUtils.getBasename(file.getOriginalFilename());
            String extension = FilenameUtils.getExtension(file.getOriginalFilename());
            String timestamp = String.valueOf(System.currentTimeMillis());
            String upFilePath = StringUtils.join(basename, "_", timestamp, ".", extension);
            String filePath = StringUtils.join(StringUtils.appendIfMissing(StringUtils.isNotBlank(domain) ? domain : source, "/"), upFilePath);

            // Upload
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, upFilePath, file.getInputStream());
            if (putObjectResult == null) {
                throw new FileOperationException("上传附件 " + file.getOriginalFilename() + " 到阿里云失败 ");
            }

            // Response result
            UploadResult uploadResult = new UploadResult();
            uploadResult.setFilename(basename);
            uploadResult.setFilePath(StringUtils.isBlank(styleRule) ? filePath : filePath + styleRule);
            uploadResult.setKey(upFilePath);
            uploadResult.setMediaType(MediaType.valueOf(Objects.requireNonNull(file.getContentType())));
            uploadResult.setSuffix(extension);
            uploadResult.setSize(file.getSize());

            // Handle thumbnail
            if (FileHandler.isImageType(uploadResult.getMediaType())) {
                BufferedImage image = ImageIO.read(file.getInputStream());
                uploadResult.setWidth(image.getWidth());
                uploadResult.setHeight(image.getHeight());
                uploadResult.setThumbPath(StringUtils.isBlank(thumbnailStyleRule) ? filePath : filePath + thumbnailStyleRule);
            }

            return uploadResult;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ossClient.shutdown();
        }

        // Build result
        UploadResult result = new UploadResult();

        log.info("File: [{}] uploaded successfully", file.getOriginalFilename());

        return result;
    }

    @Override
    public void delete(String key) {
        Assert.notNull(key, "File key must not be blank");

        // Get config
        String endPoint = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ENDPOINT).toString();
        String accessKey = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ACCESS_KEY).toString();
        String accessSecret = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_ACCESS_SECRET).toString();
        String bucketName = optionService.getByPropertyOfNonNull(AliYunProperties.OSS_BUCKET_NAME).toString();

        // Init OSS client
        OSS ossClient = new OSSClientBuilder().build(endPoint, accessKey, accessSecret);

        try {
            ossClient.deleteObject(new DeleteObjectsRequest(bucketName).withKey(key));
        } catch (Exception e) {
            throw new FileOperationException("附件 " + key + " 从阿里云删除失败", e);
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public boolean supportType(AttachmentType type) {
        return AttachmentType.ALIYUN.equals(type);
    }
}
