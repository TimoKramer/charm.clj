# charm.clj examples

## Run examples

```
clj -M -m examples.pomodoro
```
![pomodoro gif](images/pomodoro.gif)
```
clj -M -m examples.download
```
![download gif](images/download.gif)
```
clj -M -m examples.spinner-demo
```
![spinner gif](images/spinner.gif)
```
clj -M -m examples.todos
```
![todos gif](images/todos.gif)
```
clj -M -m examples.countdown
```
![countdown gif](images/countdown.gif)
```
clj -M -m examples.file-browser
```
![file browser gif](images/file_browser.gif)
```
clj -M -m examples.form
```
![form gif](images/form.gif)
```
clj -M -m examples.counter
```
![counter gif](images/counter.gif)

## native-image compilation

```bash
clj -T:build all
native-image -jar target/timer.jar -o timer
./timer -n Pomodoro 30m
```

