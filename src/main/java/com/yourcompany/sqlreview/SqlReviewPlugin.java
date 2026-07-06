package com.yourcompany.sqlreview;

import com.yourcompany.sqlreview.rules.SqlJavaRulesDefinition;
import com.yourcompany.sqlreview.rules.SqlRulesDefinition;
import com.yourcompany.sqlreview.sensor.MyBatisLambdaSensor;
import com.yourcompany.sqlreview.sensor.MyBatisXmlSensor;
import com.yourcompany.sqlreview.settings.SqlReviewProperties;
import org.sonar.api.Plugin;

/**
 * SonarQube SQL Review 插件入口
 * 注册 Sensor、规则定义和全局配置项
 *
 * @author marker
 */
public class SqlReviewPlugin implements Plugin {

    @Override
    public void define(Context context) {
        // 注册全局配置项
        context.addExtensions(SqlReviewProperties.getProperties());

        // 注册规则定义（XML + Java）
        context.addExtension(SqlRulesDefinition.class);
        context.addExtension(SqlJavaRulesDefinition.class);

        // 注册 Sensor（XML + Java Lambda）
        context.addExtension(MyBatisXmlSensor.class);
        context.addExtension(MyBatisLambdaSensor.class);
    }
}
