1. code structure is not optimal. need to split FE and BE to group better
2. need to add project documentation and architecture diagrams to the docs folder
3. need to add more tests, especially for the service layer and integration tests



Issues:
1. For demo disable TG integre, availability and so on. should be minimal 
2. Selection of trainees when add/edit session should be compacted, I mean we don't need always show all trainees
just find and add what we found. it should be dropdown with search. and trainees selection should be above time range
Time range values should be re-calculated just in case we selected trainee whos availability (in case it's set) 
conflicts with selected time range. 
3. Wehn we copy or edit or add session time should be unset just in case it's conflicting with selected trainee
availability (in case it's set) or with existing sessions etc. and in case time range is unset we still should 
see previusly selected time range until we started setting new
4. After we found the trainee and he is added to session (I see in row it's ticked) popup of found trainees
should be closed


So, when user just logs in first time he sees setup, selects who he is, sets his working hours, time step
(default is 30 min.), if he want to use locations, if he wants to share his availability. 
In his settings later he can change all these seetins and also can enable TG integration and if TG is enabled
and connected then he can use confirmations of sessions, communicate with trainee (messages must be duplicate 
in TG as well), enable reminders 1 hour before training. 
With minimal setup he can add trainees. In trainee manager in trainee full item full mode he can either create
single session or series of sessions at once. 
He can create session and set repeatition right there so that we have series of session at the same time.
If on some day there are some sessions then it affects free time chips. right from clicking on free time chips 
coach can create session quicly. And he can see clearly his free time. it's very convenient. He can see his
free time on day view. On plan view he sees all sessions for all trainees and he can filter them by trainee.
Also it's possible to copy session to another day or time etc. Also user sees for every day in days row
how many sessions are there for that day. Free time can be copied for the day and sent pasted to the chat with 
trainee. 

Additional options:
1. User can install app - will be more convenient then to use service.
2. User can enable multi-locations every of which can have it's own color. User can set google link from google
map manualy to locaion and then quicly copy from here for potential trainees, clients etc. 
2. User can upload photo of himself and of trainees
3. To enable option Ділится доступністю - and then Доступність tab will appear. 
Coach can set his availability. Availability can be set for every location, for every day and the be shared
as permanent link in social media and as picture can be shared for particular day.
4. Інтеграція з телеграм. if it's enabled then coach should connect to tg - just need to click the button
After he is connected to TG he can enable Sessions confirmations which will be visible on the session
Coach can give to trainee then link to trainee's personal app site where trainee can see his sessions,
can connect to TG as well and then to confirm sessions rifght from TG notifications in notification bar on
his phone. Also if TG is connected coach can give a feedback to trainee after the session as well as 
trainee can do that from his site. Also then optionally in TG can be enabled reminders 1 hour before session.

Coach then can ask trainee(s) to set there availability or trainees can do that by their wish. And then 
it will be visible for coach which time trainee can do training sessoin and create session from session
items. 


I want e2e tests to be implemented and cover every aspect of my service. existing should be revised and those not having sense - deleted. if it's better - then even create all of them from scratch. I want e2e test to cover    
in the way that any breaking of app would emmidetaly be reflected in failing e2e tests. Should cover scenarios with settin availability with sevearl intervals, locations, with sharing the free time with setting of             
availaiblity of trainee, of coach etc. should cover without locaions or with them. basically - you need to come up with great scenarios 