# charm.clj examples

## Run examples

```
bb cheatsheet
```
![cheatsheet gif](images/cheatsheet.gif)
```
bb pomodoro
```
![pomodoro gif](images/pomodoro.gif)
```
bb download
```
![download gif](images/download.gif)
```
bb spinner
```
![spinner gif](images/spinner.gif)
```
bb todos
```
![todos gif](images/todos.gif)
```
bb countdown
```
![countdown gif](images/countdown.gif)
```
bb file-browser
```
![file browser gif](images/file_browser.gif)
```
bb form
```
![form gif](images/form.gif)
```
bb counter
```
![counter gif](images/counter.gif)
```
bb timer 5
```
![timer gif](images/timer.gif)
```
bb sketch
```
![sketch gif](images/sketch.gif)
```
bb emojis
```
![emojis gif](images/emojis.gif)

## native-image compilation

```bash
clj -T:build all
native-image -jar target/timer.jar -o timer
./timer -n Pomodoro 30m
```
