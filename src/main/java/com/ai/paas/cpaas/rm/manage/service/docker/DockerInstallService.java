package com.ai.paas.cpaas.rm.manage.service.docker;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import com.ai.paas.cpaas.rm.util.AnsibleCommand;
import com.ai.paas.cpaas.rm.util.ExceptionCodeConstants;
import com.ai.paas.cpaas.rm.util.OpenPortUtil;
import com.ai.paas.cpaas.rm.util.TaskUtil;
import com.ai.paas.cpaas.rm.vo.MesosInstance;
import com.ai.paas.cpaas.rm.vo.OpenResourceParamVo;
import com.ai.paas.ipaas.PaasException;
import com.esotericsoftware.minlog.Log;

public class DockerInstallService implements Tasklet {

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
      throws Exception {
    InputStream in = OpenPortUtil.class.getResourceAsStream("/playbook/docker/dockerinstall.yml");
    String content = TaskUtil.getFile(in);
    OpenResourceParamVo openParam = TaskUtil.createOpenParam(chunkContext);
    Boolean useAgent = openParam.getUseAgent();
    String aid = openParam.getAid();
    TaskUtil.uploadFile("dockerinstall.yml", content, useAgent, aid);

    List<MesosInstance> mesosMaster = openParam.getMesosMaster();
    MesosInstance instance = mesosMaster.get(0);
    String password = instance.getPasswd();
    List<String> configvars = new ArrayList<String>();
    configvars.add("ansible_ssh_pass=" + password);
    configvars.add("ansible_become_pass=" + password);
    configvars.add("startup=" + openParam.getImagePath());
    AnsibleCommand dockerinstall =
        new AnsibleCommand(TaskUtil.getSystemProperty("filepath") + "/dockerinstall.yml",
            instance.getRoot(), configvars);
    Timestamp start = new Timestamp(System.currentTimeMillis());

    String result = new String();
    try {
      result = TaskUtil.executeFile("dockerinstall", dockerinstall.toString(), useAgent, aid);
    } catch (Exception e) {
      Log.error(e.toString());
      result = e.toString();
      throw new PaasException(ExceptionCodeConstants.DubboServiceCode.SYSTEM_ERROR_CODE,
          e.toString());
    } finally {
      // insert log and task record
      int taskId =
          TaskUtil.insertResJobDetail(start, openParam.getClusterId(), dockerinstall.toString(), 9);
      TaskUtil.insertResTaskLog(openParam.getClusterId(), taskId, result);

    }

    return RepeatStatus.FINISHED;
  }

}
