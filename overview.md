## Requests vs Tasks

A Request in Singularity is the primary API object and interaction that outside systems or users use. A Request is a request to run a task or set of tasks with particular parameters - it is analogous to a deployable artifact - a job or an API service, for example. Tasks are the actual running implementations (processes started by a Mesos executor) of Requests - a task runs on a particular slave and has a directory and logs, for instance. There can be many tasks running for a single Request (defined by the instances field on a request). If a task dies, a new task will take its place. The Request object and its fields are not changed by Singularity and can only be updated by posting a new Request object - which may specify scaling up or down the number of tasks, for instance (All Request API endpoints will simply return the Request object.)

## Scheduled Requests (Cron Jobs)

Scheduled Requests in Singularity currently support standard unix cron schedules with the addition of an optional seconds parameter (at the beginning). Singularity will run these Requests on the specified schedule forever. There is one notable difference between execution of scheduled tasks inside of Singularity and that of a normal crontab: Singularity will only run a single instance of a given scheduled Request at a time. This has implications for when Singularity should schedule a Request after it completes. In standard cron, a new task would be spawned every single time the cron schedule matched. In Singularity, tasks are scheduled initially based on current time, and then scheduled afterwords based on the start time of the previously executed task. For example, if a task is scheduled to run every 5 minutes, but always takes 6 minutes to run, it will essentially always be executing. Furthermore, if a task is scheduled to run at 1pm everyday, and takes just over 24 hours, it will also always be running. An important thing to note is because each new task is scheduled based on the start time of the previous task, there is not an accumulation of debt (beyond a single task.) Scheduled tasks can also be retried on failures, which is useful if a task runs infrequently but should always complete successfully. This is enabled by setting a field on the Request object, numRetriesOnFailure (default is 0), to the number of times Singularity should instantly retry a failed scheduled task. Scheduled tasks can also be ran immediately via the API and UI.

## Features

#### Fault Tolerance
Singularity will detect when tasks finish, are killed, or are lost (due to hardware failure) and will start those tasks on new slaves. (See Scheduled Requests for information about exiting scheduled tasks.) If Singularity crashes or is exited, tasks it launched will continue to execute and will be recognized when a Singularity leader reregisters with Mesos. Singularity can send email notifications to Request owners when their tasks are killed or lost. Singularity also supports a Request field, maxFailuresBeforePausing (default NULL), which optionally allows Singularity to stop trying to execute a Request which is constantly failing. When a request is Paused, it will not execute new tasks and can be unpaused via API or UI. If the Request object is updated, it will also be unpaused. This protects Singularity and Mesos from constantly attempting to execute a failing task and clogging logs and consuming resource offers.

####  Webhooks
Singularity supports registering multiple webhook URIs. Singularity will forward task updates (task started, finished, etc.) to these webhooks, along with the Request object and other relevant data. This is useful to synchronize Singularity with other data sources or operationally to signal to other processes (like load balancers) that a task is now running on a particular slave or host. These webhooks are stored in a queue and will be retried, in order until they are successfully delivered and acknowledged with a 2xx status code.

#### Rack Awareness
Singularity Requests support a rackSensitive property, if set to true, Singularity will attempt to evenly distribute instances and tasks of a particular Request between racks. Additionally, Singularity will not run multiple tasks of the same rackSensitive Request on the same slave.

#### Bounce Request, Kill Task
Singularity provides an API and UI for bouncing a Request or killing an individual task. When a Request is bounced, it will temporarily oversubscribe that Request, launching new instances and waiting a configurable amount of time before killing old tasks. When a task is killed, it will be immediately killed inside Mesos but Singularity will attempt to launch a new task to take its place.

#### Decomissioning Slaves and Racks
Singularity allows slaves and/or racks to be decommissioned. Singularity attempts to keep in sync with Mesos by keeping its own list of slaves and updating this as it sees new resource offers from new slaves or Mesos notifies Singularity that a particular slave has been killed. Singularity can decommission a slave or rack which will prevent future tasks from executing on it as well as oversubscribing all Requests which are executing on that slave/rack. Similar to a Request bounce, Singularity will wait a configurable amount of time before killing old tasks executing on decommissioning racks and slaves.

## Design

Singularity uses ZooKeeper and MySQL to maintain state and primarily uses ZooKeeper to keep track of a list of active Requests and the current tasks that fulfill these Requests. MySQL is used to store the history of edits to Requests and the task history of any launched tasks. Singularity uses leader election in ZooKeeper to maintain a single leader which is capable of registering with the Mesos master; other Singularity nodes may handle web requests but do not interact directly with Mesos. Because of the ability for other nodes to edit state, Singularity often uses queues which are consumed by the Singularity leader to effect changes in Mesos. 

The Singularity leader uses a simple lock to ensure only a single Mesos API call is handled concurrently. This simplifies the processing of state inside of Singularity but also implies that operations which want to make API calls into Mesos or change state often must also acquire this lock. This is accomplished by adding operations to queues which are consumed by threads which periodically acquire this lock in order to process changes. 

Singularity requires ZooKeeper in order to operate. Singularity will abort and essentially call System.exit when it detects failures talking to ZooKeeper. Therefore, Singularity should always run with multiple instances and in an environment where it will restart on failure (for example, using Monit.) When Singularity comes back up and is elected leader, it talks to Mesos in order to reconcile state. Tasks which were running when Singularity exited will continue to run unless they finish or there is a hardware failure. Scheduled tasks that are due will not run while Singularity is not running, but will be executed upon successful startup. It should be noted that Singularity will also fail on certain exceptions from MySQL but this is being reviewed.