package com.xuecheng.manage_cms.service;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.xuecheng.framework.domain.cms.CmsConfig;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.CmsTemplate;
import com.xuecheng.framework.domain.cms.request.QueryPageRequest;
import com.xuecheng.framework.domain.cms.response.CmsCode;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_cms.config.RabbitmqConfig;
import com.xuecheng.manage_cms.dao.CmsConfigRepository;
import com.xuecheng.manage_cms.dao.CmsPageRepository;
import com.xuecheng.manage_cms.dao.CmsTemplateRepository;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;


import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;


import freemarker.template.Template;




import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Administrator
 * @version 1.0
 * @create 2018-09-12 18:32
 **/
@Service
public class PageService {

    @Autowired
    CmsPageRepository cmsPageRepository;


    @Autowired
    CmsConfigRepository cmsConfigRepository;


    @Autowired
    RestTemplate restTemplate;

    @Autowired
    CmsTemplateRepository cmsTemplateRepository;

    @Autowired
    GridFsTemplate gridFsTemplate;

    @Autowired
    GridFSBucket gridFSBucket;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 页面查询方法
     *
     * @param page             页码，从1开始记数
     * @param size             每页记录数
     * @param queryPageRequest 查询条件
     * @return
     */
    public QueryResponseResult findList(int page, int size, QueryPageRequest queryPageRequest) {

        if (queryPageRequest == null) {

            queryPageRequest = new QueryPageRequest();
        }

        //模糊查询
        ExampleMatcher exampleMatcher = ExampleMatcher.matching().withMatcher("pageAliase", ExampleMatcher.GenericPropertyMatchers.contains());

        //设置值对象
        CmsPage cmsPage = new CmsPage();
        //设置条件值(战点Id)
        // 先判断ID是不是为空
        if (StringUtils.isNotEmpty(queryPageRequest.getSiteId())) {

            cmsPage.setSiteId(queryPageRequest.getSiteId());
        }

        if (StringUtils.isNotEmpty(queryPageRequest.getPageAliase())) {

            cmsPage.setPageAliase(queryPageRequest.getPageAliase());
        }

        if (StringUtils.isNotEmpty(queryPageRequest.getTemplateId())) {

            cmsPage.setTemplateId(queryPageRequest.getTemplateId());
        }

        Example<CmsPage> example = Example.of(cmsPage, exampleMatcher);

        //分页参数
        if (page <= 0) {
            page = 1;
        }
        page = page - 1;
        if (size <= 0) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<CmsPage> all = cmsPageRepository.findAll(example, pageable);
        QueryResult queryResult = new QueryResult();
        queryResult.setList(all.getContent());//数据列表
        queryResult.setTotal(all.getTotalElements());//数据总记录数
        QueryResponseResult queryResponseResult = new QueryResponseResult(CommonCode.SUCCESS, queryResult);
        return queryResponseResult;
    }


    // 新增
    public CmsPageResult add(CmsPage cmsPage) {

        if(cmsPage == null){
            //抛出异常，非法参数异常..指定异常信息的内容

        }


        //校验页面是否存在，根据页面名称、站点Id、页面webpath查询
        CmsPage cmsPage1 = cmsPageRepository.findByPageNameAndSiteIdAndPageWebPath(cmsPage.getPageName(), cmsPage.getSiteId(), cmsPage.getPageWebPath());

        if(cmsPage1!=null){
            //页面已经存在
            //抛出异常，异常内容就是页面已经存在
            ExceptionCast.cast(CmsCode.CMS_ADDPAGE_EXISTSNAME);
        }

        //添加页面主键由spring data 自动生成
            cmsPage.setPageId(null);

            cmsPageRepository.save(cmsPage);

            return new CmsPageResult(CommonCode.SUCCESS, cmsPage);

    }

    //根据Id查询页面
    public CmsPage getById(String id) {

        Optional<CmsPage> optional  = cmsPageRepository.findById(id);

        if (optional.isPresent()){

            CmsPage cmsPage = optional.get();

            return cmsPage;
        }
        return null;
    }

    public CmsPageResult edit(String id,CmsPage cmsPage){

        CmsPage one = this.getById(id);

        if (one!=null){

            //更新模板id
            one.setTemplateId(cmsPage.getTemplateId());
            //更新所属站点
            one.setSiteId(cmsPage.getSiteId());
            //更新页面别名
            one.setPageAliase(cmsPage.getPageAliase());
            //更新页面名称
            one.setPageName(cmsPage.getPageName());
            //更新访问路径
            one.setPageWebPath(cmsPage.getPageWebPath());
            //更新物理路径
            one.setPagePhysicalPath(cmsPage.getPagePhysicalPath());
            //跟新路径
            one.setDataUrl(cmsPage.getDataUrl());
            //执行更新
            CmsPage save = cmsPageRepository.save(one);

            return  new CmsPageResult(CommonCode.SUCCESS,one);


        }
       return  new CmsPageResult(CommonCode.FAIL,null);


    }

    //删除
    public ResponseResult delete(String id){

        Optional<CmsPage> optional = cmsPageRepository.findById(id);

        if(optional.isPresent()){

            cmsPageRepository.deleteById(id);

            return  new ResponseResult(CommonCode.SUCCESS);


        }

        return  new ResponseResult(CommonCode.FAIL);

    }


     //根据id查询配置管理信息

    public CmsConfig getConfigById(String id){

        Optional<CmsConfig> optional = cmsConfigRepository.findById(id);

        if(optional.isPresent()){
            CmsConfig cmsConfig = optional.get();

        return  cmsConfig;
        }
        return  null;
    }

    //页面静态化方法
    /**
     * 静态化程序获取页面的DataUrl
     *
     * 静态化程序远程请求DataUrl获取数据模型。
     *
     * 静态化程序获取页面的模板信息
     *
     * 执行页面静态化
     */
    //页面静态化
    public String getPageHtml(String pageId){
       //获取页面模型数据
        Map model = this.getModelByPageId(pageId);
        if(model == null){
        //获取页面模型数据为空
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAISNULL);
        }
        //获取页面模板
        String templateContent = getTemplateByPageId(pageId);
        if(StringUtils.isEmpty(templateContent)){
        //页面模板为空
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        //执行静态化
        String html = generateHtml(templateContent, model);
        if(StringUtils.isEmpty(html)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_HTMLISNULL);
        }
        return html;
    }
    //页面静态化
    public String generateHtml(String template,Map model){
        try {
      //生成配置类
            Configuration configuration = new Configuration(Configuration.getVersion());
       //模板加载器
            StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
            stringTemplateLoader.putTemplate("template",template);
        //配置模板加载器
            configuration.setTemplateLoader(stringTemplateLoader);
        //获取模板

            Template template1 = configuration.getTemplate("template");
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template1, model);
            return html;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
       //获取页面模板
    public String getTemplateByPageId(String pageId){
       //查询页面信息
        CmsPage cmsPage = this.getById(pageId);
        if(cmsPage == null){
        //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //页面模板
        String templateId = cmsPage.getTemplateId();
        if(StringUtils.isEmpty(templateId)){
        //页面模板为空
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        Optional<CmsTemplate> optional = cmsTemplateRepository.findById(templateId);
        if(optional.isPresent()){
            CmsTemplate cmsTemplate = optional.get();
         //模板文件id
            String templateFileId = cmsTemplate.getTemplateFileId();
         //取出模板文件内容
            GridFSFile gridFSFile =
                    gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(templateFileId)));
         //打开下载流对象
            GridFSDownloadStream gridFSDownloadStream =
                    gridFSBucket.openDownloadStream(gridFSFile.getObjectId());
         //创建GridFsResource
            GridFsResource gridFsResource = new GridFsResource(gridFSFile,gridFSDownloadStream);
            try {
                String content = IOUtils.toString(gridFsResource.getInputStream(), "UTF-8");
                return content;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
      //获取页面模型数据
    public Map getModelByPageId(String pageId){
      //查询页面信息
        CmsPage cmsPage = this.getById(pageId);
        if(cmsPage == null){
      //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);

        }
      //取出dataUrl
        String dataUrl = cmsPage.getDataUrl();
        if(StringUtils.isEmpty(dataUrl)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAURLISNULL);
        }
        ResponseEntity<Map> forEntity = restTemplate.getForEntity(dataUrl, Map.class);
        Map body = forEntity.getBody();
        return body;
    }


    //页面发布
    public ResponseResult post(String pageId){

        //执行页面静态化
        String pageHtml = this.getPageHtml(pageId);

        //将页面静态化文件存储到GridFs中
        CmsPage cmsPage = saveHtml(pageId, pageHtml);

        //向MQ发消息
        sendPostPage(pageId);

        return new ResponseResult(CommonCode.SUCCESS);
    }

    //将页面静态化文件存储到GridFs中
    private CmsPage saveHtml(String pageId,String htmlContent){

       // 先获取页面
        CmsPage cmsPage = this.getById(pageId);
        //判断输入的ID是否正确,页面是否存在
        if (cmsPage ==null){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        ObjectId objectId=null;
        try {

            //将htmlContent内容转成输入流
            InputStream inputStream = IOUtils.toInputStream(htmlContent, "UTF-8");
            //将文件内容存储到GridFs中
           objectId = gridFsTemplate.store(inputStream, cmsPage.getPageName());

        } catch (IOException e) {
            e.printStackTrace();
        }
        //将html文件id更新到cmsPage中

        cmsPage.setHtmlFileId(objectId.toHexString());

         return    cmsPageRepository.save(cmsPage);
    }

    //向mq 发送消息
    private void sendPostPage(String pageId){

        CmsPage cmsPage = this.getById(pageId);
        if (cmsPage==null){

            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }

        //创建消息对象
        Map<String ,String> msg=new HashMap<>();

        msg.put("pageId",pageId);

        //转成json串
        String jsonString = JSON.toJSONString(msg);

        //发送给mq
        // 站点id
        String siteId = cmsPage.getSiteId();

        rabbitTemplate.convertAndSend(RabbitmqConfig.EX_ROUTING_CMS_POSTPAGE,siteId,jsonString);

    }


}
