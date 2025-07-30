package config;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import model.KOTH;
import helpers.SqlConnectorPicksPriceTable;
import services.CommonProcessingService;
import services.ServletUtility;

@Configuration
public class ServletInitializer extends SpringBootServletInitializer {

    @Autowired
    private SqlConnectorPicksPriceTable sqlConnectorPicksPriceTable;

    @Autowired
    private CommonProcessingService commonProcessingService;

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(KOTH.class);
    }

//    @PostConstruct
//    public void init() {
//        // Initialize static references in ServletUtility
//        ServletUtility.setSqlConnectorPicksPriceTable(sqlConnectorPicksPriceTable);
//        ServletUtility.setCommonProcessingService(commonProcessingService);
//        System.out.println("ServletInitializer: Static dependencies set in ServletUtility");
//    }
}

