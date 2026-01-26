# charm.clj examples

## Run examples

```
clj -M:timer
```
![timer gif](/images/timer.gif)
```
clj -M:download
```
![download gif](/images/download.gif)
```
clj -M:spinner-demo
```
![spinner gif](/images/spinner_demo.gif)
```
clj -M:todos
```
![todos gif](/images/todos.gif)
```
clj -M:countdown
```
![countdown gif](/images/countdown.gif)
```
clj -M:file-browser
```
![file browser gif](/images/file_browser.gif)
```
clj -M:form
```
![form gif](/images/form.gif)
```
clj -M:counter
```
![counter gif](/images/counter.gif)

## native-image compilation

```bash
clj -T:build all
native-image -jar target/timer.jar -o target/timer
./target/timer -n Pomodoro 30m
```

