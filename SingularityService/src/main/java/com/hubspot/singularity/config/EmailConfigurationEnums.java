package com.hubspot.singularity.config;

public class EmailConfigurationEnums {

  public enum EmailType {
    TASK_LOST, TASK_KILLED, TASK_FINISHED, TASK_FINISHED_NON_SCHEDULED_REQUEST, TASK_FAILED, TASK_SCHEDULED_OVERDUE_TO_FINISH, TASK_KILLED_DECOMISSIONED,
    TASK_KILLED_UNHEALTHY, REQUEST_IN_COOLDOWN, SINGULARITY_ABORTING, REQUEST_REMOVED, REQUEST_PAUSED, REQUEST_UNPAUSED;
  }

  public enum EmailDestination {
    OWNERS, ADMINS;
  }

}
